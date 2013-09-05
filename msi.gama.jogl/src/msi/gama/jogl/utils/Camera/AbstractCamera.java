package msi.gama.jogl.utils.Camera;

import static java.awt.event.KeyEvent.*;
import java.awt.Point;
import java.awt.event.*;
import java.nio.IntBuffer;
import java.util.Iterator;
import javax.media.opengl.GL;
import javax.media.opengl.glu.GLU;
import msi.gama.jogl.utils.JOGLAWTGLRenderer;
import msi.gama.jogl.utils.Camera.Arcball.Vector3D;
import msi.gama.metamodel.agent.IAgent;
import msi.gama.metamodel.shape.*;
import msi.gama.metamodel.topology.AbstractTopology;
import msi.gama.metamodel.topology.filter.Different;
import msi.gama.runtime.GAMA;
import com.sun.opengl.util.BufferUtil;
import com.vividsolutions.jts.geom.Envelope;

public abstract class AbstractCamera implements ICamera {

	protected static final double factor = Math.PI / 180d;

	private JOGLAWTGLRenderer renderer;
	protected boolean isMacOS = false;
	protected final IntBuffer selectBuffer = BufferUtil.newIntBuffer(1024);// will store information

	// picking
	private boolean isPickedPressed = false;
	private Point mousePosition;

	// ROI Drawing
	private boolean enableROIDrawing = false;

	protected double maxDim;

	// To handle mouse event
	private int lastxPressed;
	private int lastyPressed;

	protected final Vector3D position = new Vector3D();
	protected final Vector3D target = new Vector3D();
	protected final Vector3D forward = new Vector3D();
	protected final Vector3D upVector = new Vector3D();

	protected double theta;
	protected double phi;

	private final double _keyboardSensivity = 4.0;
	private final double _sensivity = 0.4;

	private boolean goesForward;
	private boolean goesBackward;
	private boolean strafeLeft;
	private boolean strafeRight;
	private final boolean ctrlKeyDown = false;
	private boolean shiftKeyDown = false;

	public AbstractCamera(final JOGLAWTGLRenderer renderer) {
		this.setRenderer(renderer);

		detectMacOS();
		setMousePosition(new Point(0, 0));
		upPosition(0.0, 1.0, 0.0);
	}

	public AbstractCamera(final double xPos, final double yPos, final double zPos, final double xLPos,
		final double yLPos, final double zLPos, final JOGLAWTGLRenderer renderer) {
		setRenderer(renderer);
		detectMacOS();
		setMousePosition(new Point(0, 0));
		upPosition(0.0, 1.0, 0.0);
	}

	@Override
	public void resetCamera(final double envWidth, final double envHeight, final boolean threeD) {
		setMaxDim(envWidth > envHeight ? envWidth : envHeight);
	}

	@Override
	public final void updateCamera(final GL gl, final GLU glu, final int width, final int height) {
		float aspect = (float) width / (height == 0 ? 1 : height);
		glu.gluPerspective(45.0f, aspect, 0.1f, getMaxDim() * 100);
		makeGluLookAt(glu);
		animate();
	}

	protected abstract void animate();

	protected abstract void makeGluLookAt(GLU glu);

	@Override
	public void updatePosition(final double xPos, final double yPos, final double zPos) {
		position.set(xPos, yPos, zPos);
	}

	@Override
	public void lookPosition(final double xLPos, final double yLPos, final double zLPos) {
		target.set(xLPos, yLPos, zLPos);
	}

	@Override
	public void upPosition(final double xPos, final double yPos, final double zPos) {
		upVector.set(xPos, yPos, zPos);
	}

	/* -------Get commands--------- */

	@Override
	public Vector3D getPosition() {
		return position;
	}

	/*------------------ Events controls ---------------------*/

	@Override
	public void mouseWheelMoved(final MouseWheelEvent arg0) {
		zoom(arg0.getWheelRotation() < 0);
	}

	@Override
	public void mouseMoved(final MouseEvent arg0) {
		getMousePosition().x = arg0.getX();
		getMousePosition().y = arg0.getY();
	}

	@Override
	public void mouseEntered(final MouseEvent arg0) {}

	@Override
	public void mouseExited(final MouseEvent arg0) {}

	@Override
	public void mousePressed(final MouseEvent arg0) {
		setLastxPressed(arg0.getX());
		setLastyPressed(arg0.getY());

		// Picking mode
		// if ( myRenderer.displaySurface.picking ) {
		// Activate Picking when press and right click and if in Picking mode
		if ( arg0.getButton() == 3 ) {
			this.isPickedPressed = true;
			getRenderer().setPicking(true);
			// myRenderer.drawPickableObjects();
		} else {
			getRenderer().setPicking(false);
			// }

		}

		getMousePosition().x = arg0.getX();
		getMousePosition().y = arg0.getY();
		// getRenderer().getIntWorldPointFromWindowPoint(new Point(arg0.getX(), arg0.getY()));

	}

	@Override
	public void mouseReleased(final MouseEvent arg0) {
		if ( canSelectOnRelease(arg0) && isViewIn2DPlan() && isEnableROIDrawing() ) {
			if ( arg0.isAltDown() ) {
				Iterator<IShape> shapes =
					GAMA.getSimulation()
						.getTopology()
						.getSpatialIndex()
						.allInEnvelope(
							new GamaPoint(getRenderer().roiCenter.x, -getRenderer().roiCenter.y),
							new Envelope(getRenderer().roi_List.get(0), getRenderer().roi_List.get(2),
								-getRenderer().roi_List.get(1), -getRenderer().roi_List.get(3)), new Different(), true);
				final Iterator<IAgent> agents = AbstractTopology.toAgents(shapes);
				getRenderer().displaySurface.selectSeveralAgents(agents, 0);
			}
			if ( arg0.isShiftDown() ) {
				getRenderer().ROIZoom();
			}
			setEnableROIDrawing(false);
		}
	}

	protected abstract boolean canSelectOnRelease(MouseEvent arg0);

	@Override
	public void mouseClicked(final MouseEvent arg0) {
		if ( arg0.getClickCount() > 1 ) {
			getRenderer().displaySurface.zoomFit();
		}
		if ( arg0.isShiftDown() || arg0.isAltDown() ) {
			getRenderer().displaySurface.selectRectangle = true;
			Point point = getRenderer().getIntWorldPointFromWindowPoint(new Point(arg0.getX(), arg0.getY()));

			getMousePosition().x = arg0.getX();
			getMousePosition().y = arg0.getY();
			setEnableROIDrawing(true);
			getRenderer().DrawROI();
			getRenderer().roiCenter.setLocation(point.x, point.y);

			setEnableROIDrawing(false);
		}
	}

	@Override
	public final void keyPressed(final KeyEvent arg0) {
		switch (arg0.getKeyCode()) {
			case VK_LEFT:
				this.strafeLeft = true;
				this.shiftKeyDown = checkShiftKeyDown(arg0);
				break;
			case VK_RIGHT:
				this.strafeRight = true;
				this.shiftKeyDown = checkShiftKeyDown(arg0);
				break;
			case VK_UP:
				this.goesForward = true;
				this.shiftKeyDown = checkShiftKeyDown(arg0);
				break;
			case VK_DOWN:
				this.goesBackward = true;
				this.shiftKeyDown = checkShiftKeyDown(arg0);
				break;
		}
	}

	@Override
	public final void keyReleased(final KeyEvent arg0) {
		switch (arg0.getKeyCode()) {
			case VK_LEFT: // player turns left (scene rotates right)
				this.strafeLeft = false;
				break;
			case VK_RIGHT: // player turns right (scene rotates left)
				this.strafeRight = false;
				break;
			case VK_UP:
				this.goesForward = false;
				break;
			case VK_DOWN:
				this.goesBackward = false;
				break;
		}
	}

	@Override
	public void keyTyped(final KeyEvent arg0) {}

	// add for check Meta Button pressed in MAC Os or Ctrl in Window OS
	protected boolean checkCtrlKeyDown(final MouseEvent mouseEvent) {
		boolean specicalKeyDown = false;
		if ( isMacOS ) {
			specicalKeyDown = mouseEvent.isMetaDown();
		} else {
			specicalKeyDown = mouseEvent.isControlDown();
		}
		return specicalKeyDown;
	}

	// add for check Meta Button pressed in MAC Os or Ctrl in Window OS
	protected boolean checkCtrlKeyDown(final KeyEvent event) {
		boolean specicalKeyDown = false;
		if ( isMacOS ) {
			specicalKeyDown = event.isMetaDown();
		} else {
			specicalKeyDown = event.isControlDown();
		}
		return specicalKeyDown;
	}

	protected boolean checkShiftKeyDown(final KeyEvent event) {
		return event.isShiftDown();
	}

	protected boolean checkShiftKeyDown(final MouseEvent event) {
		return event.isShiftDown();
	}

	protected boolean detectMacOS() {
		String os = System.getProperty("os.name");
		if ( "Mac OS X".equals(os) ) {
			isMacOS = true;
		}
		return isMacOS;
	}

	protected boolean isArcBallOn(final MouseEvent mouseEvent) {
		if ( checkCtrlKeyDown(mouseEvent) && getRenderer().displaySurface.arcball == true ) { return false; }
		if ( checkCtrlKeyDown(mouseEvent) || getRenderer().displaySurface.arcball == true ) { return true; }
		return false;
	}

	// Picking method
	// //////////////////////////////////////////////////////////////////////////////////////
	/**
	 * First pass pepare select buffer for select mode by clearing it,
	 * prepare openGL to select mode and tell it where should draw
	 * object by using gluPickMatrix() method
	 * @return if returned value is true that mean the picking is enabled
	 */
	@Override
	public boolean beginPicking(final GL gl) {
		if ( !isPickedPressed ) { return false; }
		GLU glu = new GLU();

		// 1. Selecting buffer
		selectBuffer.clear(); // prepare buffer for new objects
		gl.glSelectBuffer(selectBuffer.capacity(), selectBuffer);// add buffer to openGL

		// Pass below is very similar to refresh method in GLrenderer
		// 2. Take the viewport attributes,
		int viewport[] = new int[4];
		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);

		int width = viewport[2]; // get width and
		int height = viewport[3]; // height from viewport

		// 3. Prepare openGL for rendering in select mode
		gl.glRenderMode(GL.GL_SELECT);

		/*
		 * The application must redefine the viewing volume so that it renders only a small
		 * area around the place where the mouse was clicked. In order to do that it is
		 * necessary to set the matrix mode to GL_PROJECTION. Afterwards, the application
		 * should push the current matrix to save the normal rendering mode settings.
		 * Next initialise the matrix
		 */

		gl.glMatrixMode(GL.GL_PROJECTION);
		gl.glPushMatrix();
		gl.glLoadIdentity();

		/*
		 * Define the viewing volume so that rendering is done only in a small area around
		 * the cursor. gluPickMatrix method restrict the area where openGL will drawing objects
		 * 
		 * OpenGL has a different origin for its window coordinates than the operation system.
		 * The second parameter provides for the conversion between the two systems, i.e. it
		 * transforms the origin from the upper left corner, into the bottom left corner
		 */
		glu.gluPickMatrix(getMousePosition().x, height - getMousePosition().y, 4, 4, viewport, 0);

		this.updateCamera(gl, glu, width, height);
		// FIXME: Comment GL_MODELVIEW to debug3D picking (redraw the model when clicking)
		gl.glMatrixMode(GL.GL_MODELVIEW);
		// 4. After this pass you must draw Objects

		return true;
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	/**
	 * After drawing we have to calculate which object was nearest screen and return its index
	 * @return name of selected object
	 */
	@Override
	public int endPicking(final GL gl) {
		if ( !isPickedPressed ) { return -1; }
		this.isPickedPressed = false;// no further iterations
		int selectedIndex;

		// 5. When you back to Render mode gl.glRenderMode() methods return number of hits
		int howManyObjects = gl.glRenderMode(GL.GL_RENDER);

		// 6. Restore to normal settings
		gl.glMatrixMode(GL.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL.GL_MODELVIEW);

		// 7. Seach the select buffer to find the nearest object

		// code below derive which ocjects is nearest from monitor
		//
		if ( howManyObjects > 0 ) {
			// simple searching algorithm
			selectedIndex = selectBuffer.get(3);
			int mindistance = Math.abs(selectBuffer.get(1));
			for ( int i = 0; i < howManyObjects; i++ ) {

				if ( mindistance < Math.abs(selectBuffer.get(1 + i * 4)) ) {

					mindistance = Math.abs(selectBuffer.get(1 + i * 4));
					selectedIndex = selectBuffer.get(3 + i * 4);

				}

			}
			// end of searching
		} else {
			selectedIndex = -2;// return -2 of there was no hits
		}

		return selectedIndex;
	}

	@Override
	public double getMaxDim() {
		return maxDim;
	}

	protected void setMaxDim(final double maxDim) {
		this.maxDim = maxDim;
	}

	protected void dump() {
		System.out.println("xPos:" + position.x + " yPos:" + position.y + " zPos:" + position.z);
		System.out.println("xLPos:" + target.x + " yLPos:" + target.y + " zLPos:" + target.z);
		System.out.println("_phi " + phi + " _theta " + theta);
	}

	@Override
	public void doInertia() {
		// Nothing to do by default
	}

	@Override
	public Point getMousePosition() {
		return mousePosition;
	}

	protected void setMousePosition(final Point mousePosition) {
		this.mousePosition = mousePosition;
	}

	@Override
	public boolean isEnableROIDrawing() {
		return enableROIDrawing;
	}

	protected void setEnableROIDrawing(final boolean enableROIDrawing) {
		this.enableROIDrawing = enableROIDrawing;
	}

	@Override
	public int getLastxPressed() {
		return lastxPressed;
	}

	protected void setLastxPressed(final int lastxPressed) {
		this.lastxPressed = lastxPressed;
	}

	@Override
	public int getLastyPressed() {
		return lastyPressed;
	}

	protected void setLastyPressed(final int lastyPressed) {
		this.lastyPressed = lastyPressed;
	}

	protected double get_keyboardSensivity() {
		return _keyboardSensivity;
	}

	protected double get_sensivity() {
		return _sensivity;
	}

	protected boolean isCtrlKeyDown() {
		return ctrlKeyDown;
	}

	protected boolean isShiftKeyDown() {
		return shiftKeyDown;
	}

	protected boolean isForward() {
		return goesForward;
	}

	protected boolean isBackward() {
		return goesBackward;
	}

	protected boolean isStrafeLeft() {
		return strafeLeft;
	}

	protected boolean isStrafeRight() {
		return strafeRight;
	}

	@Override
	public void zeroVelocity() {
		// Nothing to do by default
	}

	protected JOGLAWTGLRenderer getRenderer() {
		return renderer;
	}

	protected void setRenderer(final JOGLAWTGLRenderer renderer) {
		this.renderer = renderer;
	}

	@Override
	public double getPhi() {
		return phi;
	}

}
