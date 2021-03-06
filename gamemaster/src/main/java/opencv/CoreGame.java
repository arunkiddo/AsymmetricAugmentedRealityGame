//Adapted from https://github.com/bytedeco/javacv/blob/master/samples/ColoredObjectTrack.java
package opencv;

import static org.bytedeco.javacpp.opencv_core.cvCreateImage;
import static org.bytedeco.javacpp.opencv_core.cvGetSize;
import static org.bytedeco.javacpp.opencv_core.cvInRangeS;
import static org.bytedeco.javacpp.opencv_core.cvScalar;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvLoadImage;
import static org.bytedeco.javacpp.opencv_imgproc.CV_MEDIAN;
import static org.bytedeco.javacpp.opencv_imgproc.cvCircle;
import static org.bytedeco.javacpp.opencv_imgproc.cvCvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.cvGetCentralMoment;
import static org.bytedeco.javacpp.opencv_imgproc.cvGetSpatialMoment;
import static org.bytedeco.javacpp.opencv_imgproc.cvMoments;
import static org.bytedeco.javacpp.opencv_imgproc.cvSmooth;

import java.util.ArrayList;
import java.util.Random;

import javax.swing.JFrame;

import org.bytedeco.javacpp.opencv_core.CvScalar;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_imgproc.CvMoments;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.joda.time.DateTime;

import comm.WebAPI;
import logic.GamesHolder;

public class CoreGame implements Runnable {

	enum GameState {
		Menu, Calibration, Playing, GameOver
	}

	private GameState gameState = GameState.Calibration;

	private ArrayList<Bomb> bombs = new ArrayList<Bomb>();
	private Random random = new Random();
	public CameraController cameraController;
	private GamesHolder gamesHolder;
	private WebAPI webApi;
	private int bombIdCounter = 1;
	private FrameGrabber grabber;
	private OpenCVFrameConverter.ToIplImage converter;
	private IplImage grabbedImage;
	private IplImage annotatedImage;
	private IplImage thresholdedImage;
	private IplImage bombImage;

	// int a = 0, b = 0, c = 0, d = 100, e = 255, f = 255;
	CvScalar rgba_min = cvScalar(0, 0, 0, 0);
	CvScalar rgba_max = cvScalar(100, 255, 255, 0);

	private OverlayCanvasFrame videoFrame;
	private CanvasFrame thresholdedVideoFrame;
	private ColorValueControlInterface colorinterface;

	private double spawnIntervalMin = 10;
	private double spawnIntervalMax = 20;
	private double spawnTimer = 0;
	private double nextSpawn = 0;
	private double gameOverTimer = 0;
	private int bombMinSeconds = 15;
	private int bombRandSeconds = 20;

	private double defuseDistance = 8;
	private float playerBearing = 0;
	private boolean playerDetected = false;

	public static void main(String[] args) {
		CoreGame cot = new CoreGame();
		Thread th = new Thread(cot);
		th.start();
	}

	public void SpawnBomb() {
		DateTime dateTime = new DateTime();
		dateTime = dateTime.plusSeconds(bombMinSeconds + random.nextInt(bombRandSeconds));
		bombs.add(new Bomb(bombIdCounter, -180 + random.nextFloat() * 360, dateTime));
		//bombs.add(new Bomb(bombIdCounter, 0, dateTime));
		gamesHolder.addBomb(bombIdCounter, dateTime);
		bombIdCounter++;
		System.out.println("Bomb spawned at " + bombs.get(bombs.size() - 1).getBearing());
	}

	public boolean canDefuseBomb(int bombId) {
		for (int i = 0; i < bombs.size(); i++) {
			if (bombs.get(i).getId() == bombId) {
				System.out.println("angle difference "
						+ Math.abs(Utility.angleDifference(playerBearing, bombs.get(i).getBearing())));
				if (!bombs.get(i).hasExploded() && Math
						.abs(Utility.angleDifference(playerBearing, bombs.get(i).getBearing())) <= defuseDistance) {
					System.out.println("can defuse bomb");
					return true;
					// TODO: Put in a nice animation (or at least a different
					// image) for this
				}
			}
		}
		return false;
	}

	public void defuseBomb(int bombId) {
		for (int i = 0; i < bombs.size(); i++) {
			if (bombs.get(i).getId() == bombId) {
				System.out.println("Defusing bomb");
				bombs.remove(i);
				break;
			}
		}
	}

	public void tryDefuseAll() {
		for (int i = 0; i < bombs.size(); i++) {
			if (canDefuseBomb(bombs.get(i).getId())) {
				defuseBomb(bombs.get(i).getId());
				break;
			}
		}
	}

	private void explodeAll() {
		for (int i = 0; i < bombs.size(); i++) {
			if (!bombs.get(i).hasStartedToExplode())
				bombs.get(i).explode();
		}
	}

	public CoreGame() {
		gamesHolder = new GamesHolder(this);
		webApi = new WebAPI(gamesHolder);
	}

	public void updatergbvalues(int a, int b, int c, int d, int e, int f) {
		rgba_min = cvScalar(a, b, c, 0);
		rgba_max = cvScalar(d, e, f, 0);
	}

	public void updatergbvaluesFromFile(int a, int b, int c, int d, int e, int f) {

		rgba_min = cvScalar(a, b, c, 0);
		rgba_max = cvScalar(d, e, f, 0);
	}

	public void run() {
		GameLoop();
	}

	private void GameLoop() {
		setupCamera("192.168.20.254");

		//setupCamera(null);
		setupWindows();
		Thread thPan = new Thread(cameraController);
		thPan.start();
		loadBombImage();

		long lastLoopTime = System.nanoTime();
		boolean gameRunning = true;
		while (gameRunning) {

			long now = System.nanoTime();
			long updateLength = now - lastLoopTime;
			double updateLengthSeconds = (double) updateLength / 1000000000.0;
			lastLoopTime = now;

			GrabFrame();

			switch (gameState) {
			case Calibration:
				thresholdedVideoFrame.showImage(converter.convert(thresholdedImage));
				break;
			case Playing:
				Update(updateLengthSeconds);
				trackAndAnnotate();
				videoFrame.showImage(converter.convert(annotatedImage));
				break;
			case GameOver:
				Update(updateLengthSeconds);
				trackAndAnnotate();
				videoFrame.showImage(converter.convert(annotatedImage));
				break;
			default:
			}
		}
	}

	public void play() {
		
		videoFrame.setVisible(true);
		thresholdedVideoFrame.setVisible(false);
		colorinterface.hide();
		gameState = GameState.Playing;
		gamesHolder.startGame("Game 1");
		videoFrame.requestFocus();
	}

	private void loadBombImage() {
		String filename = null;
		if (System.getProperty("os.name").startsWith("Windows")) {
			filename = System.getProperty("user.dir") + "\\bomb_small.png";
		} else {
			ClassLoader classLoader = this.getClass().getClassLoader();
			filename = classLoader.getResource("bomb_small.png").getFile();
		}
		bombImage = cvLoadImage(filename, -1);
		if (bombImage == null)
			System.out.println("Failed to load bomb image");
	}

	// Connect a frame grabber to a camera and set things up
	private void setupCamera(String ip) {
		if (ip == null) {
			try {
				grabber = FrameGrabber.createDefault(0);
			} catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
				e.printStackTrace();
			}
		} else {
			grabber = new FFmpegFrameGrabber("http://root:pass@" + ip + "/axis-cgi/mjpg/video.cgi");
		}
		converter = new OpenCVFrameConverter.ToIplImage();
		try {
			grabber.start();
		} catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
			e.printStackTrace();
		}
		cameraController = new CameraController(this, ip == null ? false : true);
	}

	private void setupWindows() {
		videoFrame = new OverlayCanvasFrame("Original", cameraController, this);
		videoFrame.addKeyListener(cameraController);
		videoFrame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		videoFrame.setVisible(false);

		thresholdedVideoFrame = new CanvasFrame("Thresholded");
		thresholdedVideoFrame.addKeyListener(cameraController);
		thresholdedVideoFrame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);

		colorinterface = new ColorValueControlInterface(this);
		colorinterface.initInterface();
	}

	// Grab a frame
	private void GrabFrame() {
		try {
			grabbedImage = converter.convert(grabber.grab());
			if (grabbedImage != null) {
				if (annotatedImage == null)
					annotatedImage = cvCreateImage(cvGetSize(grabbedImage), 8, 1);
				else
					annotatedImage.release();
				annotatedImage = grabbedImage.clone();
				getThresholdImage(grabbedImage);
			}
		} catch (Exception e) {
			System.out.println("Failed to grab frame.");
		}
	}

	private void trackAndAnnotate() {
		CvMoments moments = new CvMoments();
		cvMoments(thresholdedImage, moments, 1);
		double mom10 = cvGetSpatialMoment(moments, 1, 0);
		double mom01 = cvGetSpatialMoment(moments, 0, 1);
		double area = cvGetCentralMoment(moments, 0, 0);
		int trackedPosX = (int) (mom10 / area);
		int trackedPosY = (int) (mom01 / area);
		playerDetected = false;
		// only if its a valid position
		if (trackedPosX > 0 && trackedPosY > 0) {
			playerDetected = true;
			playerBearing = cameraController.getPan() + (float) (trackedPosX - thresholdedImage.width() / 2)
					/ (float) thresholdedImage.width() * cameraController.VIEW_ANGLE;
			if (playerBearing > 180)
				playerBearing -= 360;
			if (playerBearing < -180)
				playerBearing += 360;

			cvCircle(annotatedImage, new int[] { trackedPosX, trackedPosY }, 15, new CvScalar(255, 0, 0, 0),  org.bytedeco.javacpp.opencv_imgproc.CV_FILLED, 8, 0);
		}
	}

	private void Update(double time) {
		if (gameState == GameState.Playing) {
			spawnTimer += time;
			if (spawnTimer >= nextSpawn) {
				SpawnBomb();
				spawnTimer = 0;
				nextSpawn = spawnIntervalMin + Math.random() * (spawnIntervalMax - spawnIntervalMin);
			}
			
			for (int i = 0; i < bombs.size(); i++) {
				bombs.get(i).Update(time);
				if (bombs.get(i).hasExploded() && !bombs.get(i).hasStartedToExplode()) {
					// bombs.get(i).explode();
					// System.out.println("BOOM!");
					// TODO: uncomment this
					explodeAll();
					gameState = GameState.GameOver;
					this.gameOverTimer = 10;

				}
				if (bombs.get(i).finishedExploding()) {
					bombs.remove(i);
					i--;
				}
			}
		}
		
		if(gameState == GameState.GameOver){
			for (int i = 0; i < bombs.size(); i++) {
				bombs.get(i).Update(time);
				if (bombs.get(i).finishedExploding()) {
					bombs.remove(i);
					i--;
				}
			}
			gameOverTimer -= time;
			if(gameOverTimer <= 0){
				Restart();
			}
		}

		
	}
	
	private void Restart(){
		gameState = GameState.Calibration;
		videoFrame.setVisible(false);
		thresholdedVideoFrame.setVisible(true);
		colorinterface.show();
		bombs.clear();
	}

	public ArrayList<Bomb> getBombs() {
		return bombs;
	}

	private void getThresholdImage(IplImage orgImg) {

		if (thresholdedImage == null)
			thresholdedImage = cvCreateImage(cvGetSize(orgImg), 8, 1);
		else
			thresholdedImage.release();
		IplImage imgHSV = cvCreateImage(cvGetSize(orgImg), 8, 1);
		imgHSV = orgImg.clone();

		// Change image color format from BGR to HSV
		cvCvtColor(orgImg, imgHSV, org.bytedeco.javacpp.opencv_imgproc.CV_BGR2HSV);
		// Threshold the image according to the calibrated values
		cvInRangeS(imgHSV, rgba_min, rgba_max, thresholdedImage);
		// Smooth out noise
		cvSmooth(thresholdedImage, thresholdedImage, CV_MEDIAN, 15, 0, 0, 0);

		imgHSV.release();
		// return thresholdedImage;
	}

}