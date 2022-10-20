package com.cloudwebrtc.webrtc;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;

import com.epson.moverio.hardware.audio.AudioManager;
import com.epson.moverio.hardware.camera.CameraDevice;
import com.epson.moverio.hardware.camera.CameraManager;
import com.epson.moverio.hardware.camera.CameraProperty;
import com.epson.moverio.hardware.camera.CaptureDataCallback;
import com.epson.moverio.hardware.camera.CaptureDataCallback2;
import com.epson.moverio.hardware.camera.CaptureStateCallback;
import com.epson.moverio.hardware.camera.CaptureStateCallback2;
import com.epson.moverio.hardware.display.DisplayManager;
import com.epson.moverio.hardware.sensor.SensorDataListener;
import com.epson.moverio.hardware.sensor.SensorManager;
import com.epson.moverio.util.PermissionHelper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

public class ByteVideoCapturer implements VideoCapturer {
    private interface VideoReader {

        VideoFrame getNextFrame();

        void close();
    }

    @SuppressWarnings("StringSplitter")

    private static class VideoByteReader implements VideoReader {
        private CameraManager cameraManager;
        private CameraDevice cameraDevice;
        private ByteBuffer lastFrame;
        private final int frameWidth;
        private final int frameHeight;
        private int FRAME_DELIMETER_LENGTH;
        private int offset = 0;
        private Context context;

        public VideoByteReader(int frameWidth, int frameHeight, Context context) {
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.context = context;
            cameraManager = new CameraManager(this.context);
            try {
                cameraDevice = cameraManager.open(captureStateCallback, mCaptureDataCallback, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private CaptureStateCallback2 captureStateCallback = new CaptureStateCallback2() {
            @Override
            public void onCameraOpened() {

            }

            @Override
            public void onCameraClosed() {

            }

            @Override
            public void onCaptureStarted() {

            }

            @Override
            public void onCaptureStopped() {

            }

            @Override
            public void onPreviewStarted() {

            }

            @Override
            public void onPreviewStopped() {

            }

            @Override
            public void onRecordStarted() {

            }

            @Override
            public void onRecordStopped() {

            }

            @Override
            public void onPictureCompleted() {

            }
        };

        private CaptureDataCallback mCaptureDataCallback = new CaptureDataCallback() {
            @Override
            public void onCaptureData(long l, byte[] bytes) {
                lastFrame = ByteBuffer.wrap(bytes);
                FRAME_DELIMETER_LENGTH = bytes.length;
            }
        };

        @Override
        public VideoFrame getNextFrame() {
            final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            final JavaI420Buffer buffer = JavaI420Buffer.allocate(frameWidth, frameHeight);

            final ByteBuffer dataY = buffer.getDataY();
            final ByteBuffer dataU = buffer.getDataU();
            final ByteBuffer dataV = buffer.getDataV();
            final int chromaHeight = (frameHeight + 1) / 2;
            final int sizeY = frameHeight * buffer.getStrideY();
            final int sizeU = chromaHeight * buffer.getStrideU();
            final int sizeV = chromaHeight * buffer.getStrideV();

            // Convert to YUV
            byte[] frame = new byte[FRAME_DELIMETER_LENGTH];
            frame = lastFrame.array();

            dataY.put(frame);
            dataU.put(frame);
            dataV.put(frame);

            return new VideoFrame(buffer, 0 /* rotation */, captureTimeNs);
        }

        @Override
        public void close() {
        }
    }

    private final static String TAG = "FileVideoCapturer";
    private final VideoReader videoReader;
    private CapturerObserver capturerObserver;
    private Context context;
    private final Timer timer = new Timer();

    private final TimerTask tickTask = new TimerTask() {
        @Override
        public void run() {
            tick();
        }
    };

    public ByteVideoCapturer(
            int frameWidth,
            int frameHeight) throws IOException {

        videoReader = new VideoByteReader(frameWidth, frameHeight);

    }

    public void tick() {
        VideoFrame videoFrame = videoReader.getNextFrame();
        capturerObserver.onFrameCaptured(videoFrame);
        videoFrame.release();
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
            CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
        this.context = applicationContext;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        timer.schedule(tickTask, 0, 1000 / framerate);
    }

    @Override
    public void stopCapture() throws InterruptedException {
        timer.cancel();
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        // Empty on purpose
    }

    @Override
    public void dispose() {
        videoReader.close();
    }

    @Override
    public boolean isScreencast() {
        return false;
    }
}