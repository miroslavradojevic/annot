import ij.*;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

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

	String      gndtth_path;

	// categories of critical points (indexes are used to differentiate in .swc and colours in overlays)
	public static Color		end_color = Color.YELLOW; 		// for overlay
	public static int			end_type = 1; 					// for swc

	public static Color 		bif_color = Color.RED;
	public static int			bif_type = 3;

	public static Color		cross_color = Color.GREEN;
	public static int			cross_type = 4;

	public static Color		none_color = Color.BLUE;
	public static int			none_type = 0; // last one approved by swc format

	public static
	Color		ignore_color = new Color(1, 1, 1, 0.5f);
	public static int			ignore_type = 7;

	Overlay ov_annot; 	// overlay with annotations (end, bif, cross, none colored in different colors)

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

		pick_R = Math.min(inimg.getWidth(), inimg.getHeight()) / 30f; // initial size of the circle
		pick_X = 0;
		pick_Y = 0;

		image_dir = inimg.getOriginalFileInfo().directory; 	//  + File.separator  + image_name
		image_name = inimg.getShortTitle();

		gndtth_path 				= image_dir + image_name + "_annotation.swc";

		// look for the annotations and initialize Overlays
		ov_annot = new Overlay();

		int nr_bifs 	= 0;
		int nr_cross 	= 0;
		int nr_ends 	= 0;
		int nr_nons 	= 0;
		int nr_ignores = 0;

		if ((new File(gndtth_path)).exists()) { // if it exists - update the overlays first

			ReadSWC reader = new ReadSWC(gndtth_path);

			for (int i=0; i<reader.nodes.size(); i++) {

				int   type 	= Math.round(reader.nodes.get(i)[reader.TYPE]);
				float x 	= reader.nodes.get(i)[reader.XCOORD];
				float y 	= reader.nodes.get(i)[reader.YCOORD];
				float r 	= reader.nodes.get(i)[reader.RADIUS];

				/*
				file -> overlay
				 */
				OvalRoi c = new OvalRoi(x-r+.0f, y-r+.0f, 2*r, 2*r);

				if (type==none_type) {
					c.setFillColor(none_color);
					c.setStrokeColor(none_color);
					nr_nons++;
				}
				else if (type==end_type) {
					c.setFillColor(end_color);
					c.setStrokeColor(end_color);
					nr_ends++;
				}
				else if (type==bif_type) {
					c.setFillColor(bif_color);
					c.setStrokeColor(bif_color);
					nr_bifs++;
				}
				else if (type==cross_type) {
					c.setFillColor(cross_color);
					c.setStrokeColor(cross_color);
					nr_cross++;
				}
				else if (type==ignore_type) {
					c.setFillColor(ignore_color);
					c.setStrokeColor(ignore_color);
					nr_ignores++;
				}

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

		IJ.showStatus("loaded " + nr_bifs + " bifs, " + nr_ends + " ends, " + nr_nons + " nons, " + nr_cross + " crosses, " + nr_ignores + " ignores");
		IJ.setTool("hand");

	}

	private void updateCircle() {

		// update the pick circle

		OvalRoi circ_to_add = new OvalRoi(pick_X-pick_R+.0f, pick_Y-pick_R+.0f, 2*pick_R, 2*pick_R);
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

			float ovl_x = (float) (ov_annot.get(i).getFloatBounds().getX() +  ov_annot.get(i).getFloatBounds().getWidth()/2f - .0f);
			float ovl_y = (float) (ov_annot.get(i).getFloatBounds().getY() + ov_annot.get(i).getFloatBounds().getHeight()/2f - .0f);
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



		// use pick_X, pick_Y and pick_R to check if it does not overlap
		for (int i = 0; i < ov_annot.size()-1; i++) { // check all the previous ones

			float x = (float) ov_annot.get(i).getFloatBounds().getX();
			float y = (float) ov_annot.get(i).getFloatBounds().getY();
			float w = (float) ov_annot.get(i).getFloatBounds().getWidth();
			float h = (float) ov_annot.get(i).getFloatBounds().getHeight();
			float r = (float) (Math.max(w, h)/2);

			Color cl = ov_annot.get(i).getFillColor();

			x = x+r/1-.0f;
			y = y+r/1-.0f;

			boolean overlap = (x-pick_X)*(x-pick_X)+(y-pick_Y)*(y-pick_Y)<=(r+pick_R)*(r+pick_R);

			// allow only ignores on top of ignores
			if (col.equals(ignore_color)) {

				if (overlap && !cl.equals(ignore_color)) {
					IJ.showStatus("ignore cannot be added on top of existing non-ignore");
					return;
				}

			}
			else {

				if (overlap) {
					IJ.showStatus("non-ignore cannot be added on top of anything");
					return;
				}

			}



		}

		OvalRoi cc = new OvalRoi(pick_X-pick_R+.0f, pick_Y-pick_R+.0f, 2*pick_R, 2*pick_R);
		OvalRoi ccc = cc;
		cc.setFillColor(col);
		cc.setStrokeColor(col);

		if (begun_picking) {
			ov_annot.remove(ov_annot.size()-1);   // the last one is always the currently plotted
			ov_annot.add(cc);
			ov_annot.add(ccc);
		}

		canvas.setOverlay(ov_annot);
		canvas.getImage().updateAndDraw();

		IJ.showStatus("added, current size: " + (ov_annot.size()-1));

	}

	private void export(String gndtth_path_spec){

		PrintWriter logAnnotWriter = null;//logBifWriter=null, logEndWriter=null, logNonWriter=null; // initialize export

		// empty the contents first (assume it was loaded first)
		PrintWriter logWriter = null;
		try {
			logWriter = new PrintWriter(gndtth_path_spec);
			logWriter.print("");
			logWriter.close();
		} catch (FileNotFoundException ex) {}

		// append
		try {
			logAnnotWriter = new PrintWriter(new BufferedWriter(new FileWriter(gndtth_path_spec, true)));
		} catch (IOException e) {}

		// exports the overlay with annotations to output files in swc format
		int id= 0;

		for (int i=0; i<ov_annot.size()-1; i++) {

			/*
			overlay -> file
			 */

			float x = (float) ov_annot.get(i).getFloatBounds().getX();
			float y = (float) ov_annot.get(i).getFloatBounds().getY();
			float w = (float) ov_annot.get(i).getFloatBounds().getWidth();
			float h = (float) ov_annot.get(i).getFloatBounds().getHeight();
			float r = (float) (Math.max(w, h)/2);

			Color get_col = ov_annot.get(i).getFillColor();

			float xc = x+r/1-.0f;
			float yc = y+r/1-.0f;

			if (get_col==bif_color) {
				logAnnotWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", id++, 	bif_type, 		xc, yc, 0f,     r));
			}
			else if (get_col==end_color) {
				logAnnotWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", id++,	end_type, 		xc, yc, 0f,     r));
			}
			else if (get_col==none_color) {
				logAnnotWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", id++,	none_type, 		xc, yc, 0f,     r));
			}
			else if (get_col==cross_color) {
				logAnnotWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", id++,	cross_type, 	xc, yc, 0f,     r));
			}
			else if (get_col==ignore_color) {
				logAnnotWriter.println(String.format("%-4d %-4d %-6.2f %-6.2f %-6.2f %-3.2f -1", id++,	ignore_type, 	xc, yc, 0f,     r));
			}

		}

		logAnnotWriter.close();
		System.out.println(gndtth_path_spec);

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
		gd.addChoice("choose ", new String[]{"BIF", "CRS", "END", "NON", "IGNORE"}, "NON");
		gd.showDialog();

		if (gd.wasCanceled()) return;

		String aa = gd.getNextChoice();

		if (aa.equals("BIF")) {
			addCircle(bif_color);
		}
		if (aa.equals("END")) {
			addCircle(end_color);
		}
		if (aa.equals("NON")) {
			addCircle(none_color);
		}
		if (aa.equals("CRS")) {
			addCircle(cross_color);
		}
		if (aa.equals("IGNORE")) {
			addCircle(ignore_color);
		}

	}

	public void keyTyped(KeyEvent e) {

		if (e.getKeyChar()=='u') pick_R += 1;
		if (e.getKeyChar()=='j') pick_R -= 1;
		if (e.getKeyChar()=='+') canvas.zoomIn((int) pick_X, (int) pick_Y);
		if (e.getKeyChar()=='-') canvas.zoomOut((int) pick_X, (int) pick_Y);
		if (e.getKeyChar()=='b' || e.getKeyChar()=='3') addCircle(bif_color);
		if (e.getKeyChar()=='e' || e.getKeyChar()=='1') addCircle(end_color);
		if (e.getKeyChar()=='n' || e.getKeyChar()=='0') addCircle(none_color);
		if (e.getKeyChar()=='c' || e.getKeyChar()=='4') addCircle(cross_color);
		if (e.getKeyChar()=='i' || e.getKeyChar()=='7') addCircle(ignore_color);
		if (e.getKeyChar()=='d') removeCircle();
		if (e.getKeyChar()=='s') {
			GenericDialog gd = new GenericDialog("Save?");
			gd.addStringField("output path", gndtth_path, gndtth_path.length()+10);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			String gndtth_path_spec = gd.getNextString();
			export(gndtth_path_spec);
		}

		updateCircle();

	}

	public void imageClosed(ImagePlus imagePlus) {

		if (wind!=null)
			wind.removeKeyListener(this);
		if (canvas!=null)
			canvas.removeKeyListener(this);
		ImagePlus.removeImageListener(this);

		GenericDialog gd = new GenericDialog("Save?");
		gd.addStringField("output path", gndtth_path, gndtth_path.length()+10);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		String gndtth_path_spec = gd.getNextString();
		export(gndtth_path_spec);

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