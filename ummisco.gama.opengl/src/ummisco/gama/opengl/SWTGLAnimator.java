package ummisco.gama.opengl;

import java.io.PrintStream;
import com.jogamp.opengl.*;

/**
 * Simple Animator (with target FPS)
 * 
 * @author AqD (aqd@5star.com.tw)
 */
public class SWTGLAnimator implements Runnable, GLAnimatorControl {

	protected final int targetFPS;
	protected final Thread animatorThread;
	GLAutoDrawable drawable;
	// protected final CopyOnWriteArrayList<GLAutoDrawable> autoDrawableList = new CopyOnWriteArrayList();

	protected volatile boolean stopRequested = false;
	protected volatile boolean pauseRequested = false;
	protected volatile boolean animating = false;

	public SWTGLAnimator(final int targetFPS) {
		this.targetFPS = targetFPS;
		this.animatorThread = new Thread(this, this.getClass().getSimpleName());
		this.animatorThread.setDaemon(true);
	}

	@Override
	public void setUpdateFPSFrames(final int frames, final PrintStream out) {
		//
	}

	@Override
	public void resetFPSCounter() {
		//
	}

	@Override
	public int getUpdateFPSFrames() {
		return 0;
	}

	@Override
	public long getFPSStartTime() {
		return 0;
	}

	@Override
	public long getLastFPSUpdateTime() {
		return 0;
	}

	@Override
	public long getLastFPSPeriod() {
		return 0;
	}

	@Override
	public float getLastFPS() {
		return 0;
	}

	@Override
	public int getTotalFPSFrames() {
		return 0;
	}

	@Override
	public long getTotalFPSDuration() {
		return 0;
	}

	@Override
	public float getTotalFPS() {
		return 0;
	}

	@Override
	public boolean isStarted() {
		return this.animatorThread.isAlive();
	}

	@Override
	public boolean isAnimating() {
		return this.animating && !pauseRequested;
	}

	@Override
	public boolean isPaused() {
		return isStarted() && pauseRequested;
	}

	@Override
	public Thread getThread() {
		return this.animatorThread;
	}

	@Override
	public boolean start() {
		this.stopRequested = false;
		this.pauseRequested = false;
		this.animatorThread.start();
		return true;
	}

	@Override
	public boolean stop() {
		this.stopRequested = true;
		try {
			this.animatorThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			this.stopRequested = false;
		}
		return true;
	}

	@Override
	public boolean pause() {
		pauseRequested = true;
		return true;
	}

	@Override
	public boolean resume() {
		pauseRequested = false;
		return true;
	}

	@Override
	public void add(final GLAutoDrawable drawable) {
		// this.autoDrawableList.addIfAbsent(drawable);
		if ( this.drawable != null ) {
			remove(this.drawable);
		}
		this.drawable = drawable;
		drawable.setAnimator(this);
	}

	@Override
	public void remove(final GLAutoDrawable drawable) {
		// this.autoDrawableList.remove(drawable);
		if ( this.drawable == drawable ) {
			this.drawable = null;
			drawable.setAnimator(null);
		}
	}

	@Override
	public void run() {
		long frameDuration = 1000 / this.targetFPS;
		while (!this.stopRequested) {
			if ( !pauseRequested ) {
				long timeBegin = System.currentTimeMillis();
				this.displayGL();
				long timeUsed = System.currentTimeMillis() - timeBegin;
				long timeSleep = frameDuration - timeUsed;
				if ( timeSleep >= 0 ) {
					try {
						Thread.sleep(timeSleep);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	protected void displayGL() {
		this.animating = true;
		try {
			if ( drawable != null && drawable.isRealized() ) {
				drawable.display();
			}
		} finally {
			this.animating = false;
		}
	}

	/**
	 * Method getUncaughtExceptionHandler()
	 * @see com.jogamp.opengl.GLAnimatorControl#getUncaughtExceptionHandler()
	 */
	@Override
	public UncaughtExceptionHandler getUncaughtExceptionHandler() {
		return null;
	}

	/**
	 * Method setUncaughtExceptionHandler()
	 * @see com.jogamp.opengl.GLAnimatorControl#setUncaughtExceptionHandler(com.jogamp.opengl.GLAnimatorControl.UncaughtExceptionHandler)
	 */
	@Override
	public void setUncaughtExceptionHandler(final UncaughtExceptionHandler handler) {}
}