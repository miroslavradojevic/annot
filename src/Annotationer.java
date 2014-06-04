//import aux.AnalyzeCSV;
//import aux.ReadSWC;
//import aux.Tools;

import ij.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;

/**
 * Created by miroslav on 6/4/14.
 */
public class Annotationer  implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

	/**
	 * searches folder where the file is to open .swc, .bif, and .end, and .non with annotations
	 * if they do not exist - it creates new annotation file with the same name as image
	 * each click will update the the current annotation list and export the final version once the annotation is
	 * finished, lists are updated with each click
	 */

	ImagePlus   inimg;
	ImageCanvas canvas;
	ImageWindow	wind;


	String      image_path;
	String 		image_dir, image_name;

	String 		gndtth_endpoints_path;
	String 		gndtth_bifurcations_path;
	String 		gndtth_nonpoints_path;

	Overlay ov_annot; 	// overlay with annotations (end, bif, non colored in different colors)

	float pick_R;        // 'pick' circle
	float pick_X;
	float pick_Y;

	boolean begun_picking;

	public void run(String s) {

		// select the image
		String in_folder = Prefs.get("id.folder", System.getProperty("user.home"));
		OpenDialog.setDefaultDirectory(in_folder);
		OpenDialog dc = new OpenDialog("Select file");
		in_folder = dc.getDirectory();
		image_path = dc.getPath();
		if (image_path==null) return;
		Prefs.set("id.folder", in_folder);

		inimg = new ImagePlus(image_path);
		if(inimg==null) return;

		pick_R = Math.min(inimg.getWidth(), inimg.getHeight()) / 30f;
		pick_X = 0;
		pick_Y = 0;

		image_dir = inimg.getOriginalFileInfo().directory; //  + File.separator  + image_name
		image_name = inimg.getShortTitle();

		gndtth_endpoints_path 		= image_dir + image_name + ".end";
		gndtth_bifurcations_path 	= image_dir + image_name + ".bif";
		gndtth_nonpoints_path 		= image_dir + image_name + ".non";

		// look for the annotations and initialize Overlays
		ov_annot = new Overlay();

		int nr_bifs = 0;
		int nr_ends = 0;
		int nr_nons = 0;

		if ((new File(gndtth_bifurcations_path)).exists()) {

			ReadSWC reader = new ReadSWC(gndtth_bifurcations_path);

			nr_bifs = reader.nodes.size();

			for (int i=0; i<reader.nodes.size(); i++) {
				float x = reader.nodes.get(i)[reader.XCOORD];
				float y = reader.nodes.get(i)[reader.YCOORD];
				float r = reader.nodes.get(i)[reader.RADIUS];

				OvalRoi c = new OvalRoi(x-r+.5f, y-r+.5f, 2*r, 2*r);
				c.setFillColor(Color.RED);
				c.setStrokeColor(Color.RED);
				ov_annot.add(c);
			}

		}

		if ((new File(gndtth_endpoints_path)).exists()) {

			ReadSWC reader = new ReadSWC(gndtth_endpoints_path);

			nr_ends = reader.nodes.size();

			for (int i = 0; i<reader.nodes.size(); i++) {
				float x = reader.nodes.get(i)[reader.XCOORD];
				float y = reader.nodes.get(i)[reader.YCOORD];
				float r = reader.nodes.get(i)[reader.RADIUS];

				OvalRoi c = new OvalRoi(x-r+.5f, y-r+.5f, 2*r, 2*r);
				c.setFillColor(Color.YELLOW);
				c.setStrokeColor(Color.YELLOW);
				ov_annot.add(c);
			}

		}

		if ((new File(gndtth_nonpoints_path)).exists()) {

			ReadSWC reader = new ReadSWC(gndtth_nonpoints_path);

			nr_nons = reader.nodes.size();

			for (int i = 0; i<reader.nodes.size(); i++) {
				float x = reader.nodes.get(i)[reader.XCOORD];
				float y = reader.nodes.get(i)[reader.YCOORD];
				float r = reader.nodes.get(i)[reader.RADIUS];

				OvalRoi c = new OvalRoi(x-r+.5f, y-r+.5f, 2*r, 2*r);
				c.setFillColor(Color.BLUE);
				c.setStrokeColor(Color.BLUE);
				ov_annot.add(c);
			}

		}

		begun_picking = false;

		inimg.show();
		wind = inimg.getWindow();
		canvas = inimg.getCanvas();
		ImageCanvas can = wind.getCanvas();

		wind.removeKeyListener(IJ.getInstance());
		can.removeKeyListener(IJ.getInstance());
		canvas.removeKeyListener(IJ.getInstance());

		canvas.addMouseListener(this);
		canvas.addMouseMotionListener(this);
		canvas.addKeyListener(this);
		wind.addKeyListener(this);
		ImagePlus.addImageListener(this);

		IJ.showStatus("loaded " + nr_bifs + " bifs, " + nr_ends + " ends, " + nr_nons + " nons");
		IJ.setTool("hand");

	}

	private void updateCircle() {

		// update the pick circle

		OvalRoi circ_to_add = new OvalRoi(pick_X-pick_R+.5f, pick_Y-pick_R+.5f, 2*pick_R, 2*pick_R);
		if (!begun_picking) {
			begun_picking = true;
			ov_annot.add(circ_to_add); // only the first time
		}
		else {
			// added already - replace the last one
			ov_annot.remove(ov_annot.size()-1);
			ov_annot.add(circ_to_add);
		}

		canvas.setOverlay(ov_annot);
		canvas.getImage().updateAndDraw();

	}

	private void removeCircle(){

		boolean found = false;

		// loop current list of detections and delete if any falls over the current position
		for (int i=0; i<ov_annot.size()-1; i++) { // the last one is always updated pick circle

			float curr_x = pick_X;
			float curr_y = pick_Y;
			float curr_r = pick_R;

			float ovl_x = (float) (ov_annot.get(i).getFloatBounds().getX() +  ov_annot.get(i).getFloatBounds().getWidth()/2f - .5f);
			float ovl_y = (float) (ov_annot.get(i).getFloatBounds().getY() + ov_annot.get(i).getFloatBounds().getHeight()/2f - .5f);
			float ovl_r = (float) (Math.max(ov_annot.get(i).getFloatBounds().getWidth(), ov_annot.get(i).getFloatBounds().getHeight()) / 2f);

			float d2 = (float) (Math.sqrt(Math.pow(curr_x-ovl_x, 2) + Math.pow(curr_y-ovl_y, 2)));
			float d2th = (float) Math.pow(curr_r+ovl_r, 1);

			if (d2<d2th) {
				found = true;
				ov_annot.remove(i);
			}

		}

		if (!found) IJ.showStatus("nothing to remove here");
		else 		IJ.showStatus("removed, current size: " + (ov_annot.size()-1));

	}

	private void addCircle(Color col){

		OvalRoi cc = new OvalRoi(pick_X-pick_R+.5f, pick_Y-pick_R+.5f, 2*pick_R, 2*pick_R);
		OvalRoi ccc = cc;
		cc.setFillColor(col);
		cc.setStrokeColor(col);

		if (begun_picking) {
			ov_annot.remove(ov_annot.size()-1);
			ov_annot.add(cc);
			ov_annot.add(ccc);
		}

		canvas.setOverlay(ov_annot);
		canvas.getImage().updateAndDraw();

		IJ.showStatus("added, current size: " + (ov_annot.size()-1));

	}

	private void export(){

		PrintWriter logBifWriter=null, logEndWriter=null, logNonWriter=null; // initialize export

		try {
			logBifWriter = new PrintWriter(new BufferedWriter(new FileWriter(gndtth_bifurcations_path, true)));
			logEndWriter = new PrintWriter(new BufferedWriter(new FileWriter(gndtth_endpoints_path, true)));
			logNonWriter = new PrintWriter(new BufferedWriter(new FileWriter(gndtth_nonpoints_path, true)));
		} catch (IOException e) {}

		// exports the overlay with annotations to output files in swc format
		int bif_id = 0;
		int end_id = 0;
		int non_id = 0;
		for (int i=0; i<ov_annot.size()-1; i++) {

			float x = (float) ov_annot.get(i).getFloatBounds().getX();
			float y = (float) ov_annot.get(i).getFloatBounds().getY();
			float w = (float) ov_annot.get(i).getFloatBounds().getWidth();
			float h = (float) ov_annot.get(i).getFloatBounds().getHeight();
			float r = (float) (Math.max(w, h)/2);

			Color get_col = ov_annot.get(i).getFillColor();

			if (get_col==Color.RED) {
				bif_id++;
				logBifWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", bif_id, 6, x+r/2-.5f, y+r/2-.5f, 0f,     r));
			}
			else if (get_col==Color.YELLOW) {
				end_id++;
				logEndWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", end_id, 6, x+r/2-.5f, y+r/2-.5f, 0f,     r));
			}
			else if (get_col==Color.BLUE) {
				non_id++;
				logNonWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", non_id, 6, x+r/2-.5f, y+r/2-.5f, 0f,     r));
			}

		}

		logBifWriter.close();
		logEndWriter.close();
		logNonWriter.close();

		System.out.println("exporting " + bif_id+ " bifs, " + end_id + " ends, " + non_id + " other");
		System.out.println(gndtth_bifurcations_path);
		System.out.println(gndtth_endpoints_path);
		System.out.println(gndtth_nonpoints_path);

	}

	public void mouseMoved(MouseEvent e) {

		pick_X = canvas.offScreenX(e.getX());
		pick_Y = canvas.offScreenY(e.getY());

		updateCircle();

	}

	public void mouseClicked(MouseEvent e) {

		pick_X = 	canvas.offScreenX(e.getX());
		pick_Y = 	canvas.offScreenY(e.getY());

		canvas.getImage().updateAndDraw();

		GenericDialog gd = new GenericDialog("CHOOSE...");
		gd.addChoice("choose ", new String[]{"BIF", "END", "NON"}, "NON");
		gd.showDialog();

		if (gd.wasCanceled()) return;

		String aa = gd.getNextChoice();

		if (aa.equals("BIF")) {
			addCircle(Color.RED);
		}
		if (aa.equals("END")) {
			addCircle(Color.YELLOW);
		}
		if (aa.equals("NON")) {
			addCircle(Color.BLUE);
		}

	}

	public void keyTyped(KeyEvent e) {

		if (e.getKeyChar()=='u') pick_R += 2;
		if (e.getKeyChar()=='j') pick_R -= 2;
		if (e.getKeyChar()=='+') canvas.zoomIn((int) pick_X, (int) pick_Y);
		if (e.getKeyChar()=='-') canvas.zoomOut((int) pick_X, (int) pick_Y);
		if (e.getKeyChar()=='b') addCircle(Color.RED);
		if (e.getKeyChar()=='e') addCircle(Color.YELLOW);
		if (e.getKeyChar()=='n') addCircle(Color.BLUE);
		if (e.getKeyChar()=='d') removeCircle();
		if (e.getKeyChar()=='s') {
			GenericDialog gd = new GenericDialog("Wanna save?");
			gd.showDialog();
			if (gd.wasCanceled()) return;
			export();
		}

		updateCircle();


	}

	public void imageClosed(ImagePlus imagePlus) {

		if (wind!=null)
			wind.removeKeyListener(this);
		if (canvas!=null)
			canvas.removeKeyListener(this);
		ImagePlus.removeImageListener(this);

		GenericDialog gd = new GenericDialog("Wanna save?");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		export();

	}

	public void imageOpened(ImagePlus imagePlus) {}
	public void imageUpdated(ImagePlus imagePlus) {}
	public void keyPressed(KeyEvent e) {}
	public void keyReleased(KeyEvent e) {}
	public void mouseDragged(MouseEvent e) {}
	public void mousePressed(MouseEvent e)  {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e)  {}
	public void mouseExited(MouseEvent e)   {}

}