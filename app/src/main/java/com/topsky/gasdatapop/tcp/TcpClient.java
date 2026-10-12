package com.topsky.gasdatapop.tcp;

import android.os.Handler;
import android.os.Looper;

import com.blankj.utilcode.util.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TcpClient {
    private static final String TAG = "TcpClient";
    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 20_000;
    private static final int RECONNECT_DELAY_BASE = 1000;
    private static final int RECONNECT_DELAY_MAX = 30000;
    private static final int SEND_QUEUE_CAPACITY = 1024;
    private static final int THREAD_JOIN_TIMEOUT_MS = 2000;

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicBoolean manualDisconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private volatile int connectionEpoch = 0;

    private String host;
    private int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readThread;
    private Thread writeThread;
    private final LinkedBlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>(SEND_QUEUE_CAPACITY);
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor();
    private Future<?> connectFuture;
    private TcpListener listener;
    private final Handler mainHandler;

    public TcpClient() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public TcpClient(String host, int port) {
        this();
        this.host = host;
        this.port = port;
    }

    public void setListener(TcpListener listener) {
        this.listener = listener;
    }

    public ConnectionState getState() {
        return state.get();
    }

    public synchronized void connect(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }

    public synchronized void connect() {
        if (host == null || port <= 0) {
            postError(new IllegalArgumentException("Host or port not set"));
            return;
        }

        ConnectionState currentState = state.get();
        if (currentState != ConnectionState.DISCONNECTED && currentState != ConnectionState.RECONNECTING) {
            LogUtils.w(TAG, "Cannot connect, current state: " + currentState);
            return;
        }

        state.set(ConnectionState.CONNECTING);
        manualDisconnect.set(false);
        reconnectCount.set(0);
        connectionEpoch++;

        if (connectFuture != null && !connectFuture.isDone()) {
            connectFuture.cancel(true);
        }

        final int currentEpoch = connectionEpoch;

        connectFuture = connectExecutor.submit(() -> {
            try {
                socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
                socket.setSoTimeout(READ_TIMEOUT);

                if (currentEpoch != connectionEpoch) {
                    closeResourcesQuietly();
                    return;
                }

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                synchronized (TcpClient.this) {
                    if (currentEpoch != connectionEpoch) {
                        closeResourcesQuietly();
                        return;
                    }
                    if (state.compareAndSet(ConnectionState.CONNECTING, ConnectionState.CONNECTED)) {
                        reconnectCount.set(0);
                        postConnected();
                        startReadWriteLoops(currentEpoch);
                    } else {
                        closeResourcesQuietly();
                    }
                }
            } catch (IOException e) {
                synchronized (TcpClient.this) {
                    if (currentEpoch == connectionEpoch && state.get() != ConnectionState.DISCONNECTED) {
                        state.set(ConnectionState.DISCONNECTED);
                        postError(e);
                        scheduleReconnect();
                    }
                }
            }
        });
    }

    private void startReadWriteLoops(int epoch) {
        startReadLoop(epoch);
        startWriteLoop(epoch);
    }

    private void startReadLoop(int epoch) {
        readThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int bytesRead;
            try {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        postReceived(data);
                    }
                }
            } catch (IOException e) {
                LogUtils.e(TAG, "Read error: " + e.getMessage());
            } finally {
                handleDisconnect(epoch);
            }
        }, "TcpRead");
        readThread.start();
    }

    private void startWriteLoop(int epoch) {
        writeThread = new Thread(() -> {
            byte[] data;
            try {
                while ((data = sendQueue.take()) != null) {
                    if (epoch != connectionEpoch) {
                        break;
                    }
                    if (state.get() != ConnectionState.CONNECTED || outputStream == null) {
                        sendQueue.clear();
                        break;
                    }
                    outputStream.write(data);
                    outputStream.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LogUtils.e(TAG, "Write error: " + e.getMessage());
                handleDisconnect(epoch);
            }
        }, "TcpWrite");
        writeThread.start();
    }

    private void handleDisconnect(int epoch) {
        synchronized (this) {
            if (epoch != connectionEpoch) {
                return;
            }
            if (state.get() == ConnectionState.DISCONNECTED) {
                return;
            }
            state.set(ConnectionState.DISCONNECTED);
            closeResources();
            postDisconnected();

            if (!manualDisconnect.get()) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        if (manualDisconnect.get()) {
            return;
        }

        int delay = (int) (RECONNECT_DELAY_BASE * Math.pow(2, Math.min(reconnectCount.getAndIncrement(), 5)));
        delay = Math.min(delay, RECONNECT_DELAY_MAX);

        String str = "Scheduling reconnect in " + delay + "ms";
        LogUtils.d(TAG, str);

        postReConnect(str);

        mainHandler.postDelayed(() -> {
            synchronized (TcpClient.this) {
                if (!manualDisconnect.get() && state.get() == ConnectionState.DISCONNECTED) {
                    state.set(ConnectionState.RECONNECTING);
                    connect();
                }
            }
        }, delay);
    }

    public synchronized void disconnect() {
        manualDisconnect.set(true);
        state.set(ConnectionState.DISCONNECTED);
        closeResources();
        mainHandler.removeCallbacksAndMessages(null);
    }

    public synchronized void release() {
        disconnect();
        listener = null;
        connectExecutor.shutdownNow();
    }

    private void closeResources() {
        sendQueue.clear();

        if (connectFuture != null) {
            connectFuture.cancel(true);
            connectFuture = null;
        }

        Thread localReadThread = readThread;
        Thread localWriteThread = writeThread;
        readThread = null;
        writeThread = null;

        Thread currentThread = Thread.currentThread();

        if (localWriteThread != null && localWriteThread != currentThread) {
            localWriteThread.interrupt();
        }

        if (localReadThread != null && localReadThread != currentThread) {
            localReadThread.interrupt();
        }

        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
        }

        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
        }

        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
        }

        if (localWriteThread != null && localWriteThread != currentThread) {
            try {
                localWriteThread.join(THREAD_JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (localReadThread != null && localReadThread != currentThread) {
            try {
                localReadThread.join(THREAD_JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void closeResourcesQuietly() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        inputStream = null;
        outputStream = null;
        socket = null;
    }

    public boolean send(byte[] data) {
        if (state.get() != ConnectionState.CONNECTED) {
            LogUtils.w(TAG, "Cannot send, not connected");
            return false;
        }

        try {
            boolean success = sendQueue.offer(data);
            if (!success) {
                LogUtils.e(TAG, "Send queue is full, data dropped");
                return false;
            }
            return true;
        } catch (Exception e) {
            LogUtils.e(TAG, "Send queue error: " + e.getMessage());
            return false;
        }
    }

    public boolean send(String message) {
        return send(message.getBytes(StandardCharsets.UTF_8));
    }

    private void postConnected() {
        mainHandler.post(() -> {
            if (listener != null) {
                try {
                    listener.onConnected();
                } catch (Exception e) {
                    LogUtils.e(TAG, "onConnected callback error: " + e.getMessage());
                }
            }
        });
    }

    private void postReConnect(String msg) {
        mainHandler.post(() -> {
            if (listener != null) {
                try {
                    listener.onReConnect(msg);
                } catch (Exception e) {
                    LogUtils.e(TAG, "onReConnect callback error: " + e.getMessage());
                }
            }
        });
    }

    private void postDisconnected() {
        mainHandler.post(() -> {
            if (listener != null) {
                try {
                    listener.onDisconnected();
                } catch (Exception e) {
                    LogUtils.e(TAG, "onDisconnected callback error: " + e.getMessage());
                }
            }
        });
    }

    private void postReceived(byte[] data) {
        mainHandler.post(() -> {
            if (listener != null) {
                try {
                    listener.onReceived(data);
                } catch (Exception e) {
                    LogUtils.e(TAG, "onReceived callback error: " + e.getMessage());
                }
            }
        });
    }

    private void postError(Exception e) {
        mainHandler.post(() -> {
            if (listener != null) {
                try {
                    listener.onError(e);
                } catch (Exception ex) {
                    LogUtils.e(TAG, "onError callback error: " + ex.getMessage());
                }
            }
        });
    }
}