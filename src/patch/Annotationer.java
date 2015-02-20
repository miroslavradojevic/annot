package patch;

import ij.*;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by miroslav on 9-2-15.
 */
public class Annotationer implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    ImagePlus inimg;
    String path;
    String annotation_id;
    String log_path;
    int log_patch_size;

    ImageCanvas canvas;
    Overlay floating_ovl = new Overlay(); // overlay that's been updated based on the category lists

    int D, N;
    int w, h;

    // 4 key annotation categories are stored in the lists              //
    ArrayList<Rectangle> NeuronList         = new ArrayList<Rectangle>();       //
    ArrayList<Rectangle> AstrocyteList      = new ArrayList<Rectangle>();       //
    ArrayList<Rectangle> BackList           = new ArrayList<Rectangle>();       //
    ArrayList<Rectangle> IgnoreList         = new ArrayList<Rectangle>();       // those that are ambiguous and avoided to annotate due to the confusion
    // Rectangle has upper-left point to mark the square

    byte[] mask_obj;    // will contain neurons, astrocytes and ignore tagged so
    byte[] mask_temp;   // will be used when random patches are created

    int currx, curry;

    int fileLength = 0;

    public void run(String s) {

        NeuronList.clear();
        AstrocyteList.clear();
        BackList.clear();
        IgnoreList.clear();

        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("annot.D", 400); // annot. square size

        GenericDialog gdG = new GenericDialog("Annotationer");
        gdG.addStringField("destination_folder", path, 80);
        gdG.addNumericField("square_size", D, 0);
        gdG.addMessage("load earlier annotation");
        gdG.addStringField("path_to_log", "NONE", 80);
        gdG.addNumericField("loaded_patch_size", -1, 0);

        gdG.showDialog();
        if (gdG.wasCanceled()) return;

        path = gdG.getNextString();
        path = new File(path).getAbsolutePath();
        D = (int) gdG.getNextNumber();
        log_path = gdG.getNextString();
        log_patch_size = (int) gdG.getNextNumber();

        if ((path.isEmpty())) return;

        if (!log_path.equalsIgnoreCase("NONE")){

            File f_log = new File(log_path);

            if (f_log.exists()) {

                log_path = f_log.getAbsolutePath();

                fileLength = 0;

                try { // scan the file

                    FileInputStream fstream 	= new FileInputStream(log_path);
                    BufferedReader br 			= new BufferedReader(new InputStreamReader(new DataInputStream(fstream)));
                    String read_line;

                    while ( (read_line = br.readLine()) != null ) {
                        if(!read_line.trim().startsWith("#")) { // # are comments

                            fileLength++;

                            // split values
                            String[] 	readLn      = 	read_line.trim().split("\\s+");
                            String      read_tag    =   readLn[0].trim();
                            int[] 	read_xywh   = 	new int[4]; // x, y, w, h

                            read_xywh[0] = Integer.valueOf(readLn[1].trim()).intValue();  // x
                            read_xywh[1] = Integer.valueOf(readLn[2].trim()).intValue();  // y
                            read_xywh[2] = Integer.valueOf(readLn[3].trim()).intValue();  // w
                            read_xywh[3] = Integer.valueOf(readLn[4].trim()).intValue();  // h

                            Rectangle rec;

                            if(log_patch_size==-1)  rec = new Rectangle(read_xywh[0], read_xywh[1], read_xywh[2], read_xywh[3]); // read the original values
                            else                    rec = new Rectangle(read_xywh[0], read_xywh[1], log_patch_size, log_patch_size);

                            if (read_tag.equalsIgnoreCase("NEURON"))
                                NeuronList.add(rec);
                            if (read_tag.equalsIgnoreCase("ASTROCYTE"))
                                AstrocyteList.add(rec);
                            if (read_tag.equalsIgnoreCase("BACKGROUD"))
                                BackList.add(rec);
                            if (read_tag.equalsIgnoreCase("IGNORE")) {
                                IgnoreList.add(rec);
                                System.out.println(Arrays.toString(readLn));
                            }
                        }
                    }

                    br.close();
                    fstream.close();

                }
                catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }

                System.out.println(log_path + " read: " + fileLength + " lines.");
                floating_ovl = generateOverlay(); // generate Overlay with the rectangles from the list
                floating_ovl.add(null);
                print();


            }
            else{
                System.out.println("log does not exist! " + log_path);
                return;
            }
        }

        Prefs.set("annot.destination_folder", path);
        Prefs.set("annot.D", D); // annot. square size

        // select the image
        String in_folder = Prefs.get("id.folder", System.getProperty("user.home"));
        OpenDialog.setDefaultDirectory(in_folder);
        OpenDialog dc = new OpenDialog("Select file");
        in_folder = dc.getDirectory();
        String image_path = dc.getPath();
        if (image_path == null) {
            return;
        }
        Prefs.set("id.folder", in_folder);

        inimg = new ImagePlus(image_path);

        if (inimg == null) return;
        if (!getFileExtension(inimg.getTitle()).equalsIgnoreCase("TIF")) {
            IJ.log("open image with .TIF extension");
            return;
        }

        File f = new File(path);
        if (!f.exists()) f.mkdirs();
        path = f.getAbsolutePath();// + File.separator + inimg.getShortTitle();
        annotation_id = f.getName();

        inimg.show();
        canvas = inimg.getCanvas();
        canvas.removeKeyListener(IJ.getInstance());
        canvas.getImage().getWindow().removeKeyListener(IJ.getInstance());
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addKeyListener(this);
        canvas.getImage().getWindow().addKeyListener(this);
        ImagePlus.addImageListener(this);

        w = inimg.getWidth();
        h = inimg.getHeight();

        mask_obj = new byte[w * h];
        mask_temp = new byte[w * h];

        IJ.setTool("hand");

    }

    public void keyTyped(KeyEvent e) {

        Rectangle rec;

        if (e.getKeyChar()=='n') { // default neuron color is red
            rec = append(currx, curry, NeuronList);
            if (rec!=null) updateOverlay(rec, Color.RED);
            print();
        }
        if (e.getKeyChar()=='a') {
            rec = append(currx, curry, AstrocyteList);
            if (rec!=null) updateOverlay(rec, Color.MAGENTA);
            print();
        }
        if (e.getKeyChar()=='d') {
            remove(currx, curry);
            print();
        }
        if (e.getKeyChar()=='i') {
            rec = append(currx, curry, IgnoreList);
            if (rec!=null) updateOverlay(rec, Color.WHITE);
            print();
        }
        if (e.getKeyChar()=='b') {

            N = (int) Prefs.get("annot.N", 30);

            GenericDialog gd = new GenericDialog("Generate background patches");
            gd.addNumericField("nr_background_patches", N, 0);
            gd.showDialog();
            if (gd.wasCanceled()) return;
            N = (int) gd.getNextNumber();
            Prefs.set("annot.N", N);

            generateObjectMap();
            randomBackground();
            print();

        }
        if (e.getKeyChar()=='e') {

            GenericDialog gd = new GenericDialog("Export?");
            gd.showDialog();
            if (gd.wasCanceled()) return;

            exportPatches();
            exportAnnots();
            inimg.setOverlay(generateOverlay());

        }

        if (e.getKeyChar()=='+') canvas.zoomIn(currx, curry);
        if (e.getKeyChar()=='-') canvas.zoomOut(currx, curry);

    }

    private void randomBackground() {

        Random r = new Random();
        int count = 0;
        int total_attempts = 0;
        int xR;
        int yR;

        byte[] uptillnow = new byte[mask_temp.length];

        System.out.print("generating background patches");

        while (count < N && total_attempts<10*N) {

            xR = r.nextInt(w-D)+D/2; // simulate mouse pinpointing
            yR = r.nextInt(h-D)+D/2;

            int xRect = Math.round(xR - D / 2f);
            int yRect = Math.round(yR - D / 2f);

                // fill the mask up
                Arrays.fill(mask_temp, (byte) 0);
                int cnt = 0;
                int cnt_wrt_objects = 0;
                for (int xloop = xRect; xloop < xRect + D; xloop++) {
                    for (int yloop = yRect; yloop < yRect + D; yloop++) {
                        mask_temp[yloop * w + xloop] = (byte) 255;
                        if ((uptillnow[yloop * w + xloop] & 0xff) > 0 ) {
                            cnt++;
                        }
                        if ( (mask_obj[yloop * w + xloop] & 0xff) > 0) { // would be enough to exclude ignore list from the object_map, but... whatta heck
                            cnt_wrt_objects++;
                        }

                    }
                }

                if (cnt==0 && cnt_wrt_objects==0) { // (cnt / (float) (D * D)) < 0.1

                    Rectangle rec = append(xR, yR, BackList); // will check iw it overlaps with background or ignore list, same as with the click

                    if (rec!=null) {

                        logic_or(uptillnow, mask_temp);

                        updateOverlay(rec, Color.BLUE);

                        count++;

                        System.out.print(".");

                    }

                }

            total_attempts++;

        }

        if (total_attempts==10*N) System.out.println("reached limit of random trials");

        System.out.println();

    }

    private Overlay generateOverlay() { // will be used at export time to generate output overlay

        Overlay ov = new Overlay();

        // loop through all of the lists and create overlays for visualization of the annotation
        // to either continue or do the same annotations with smaller size of the patch
        for (int k = 0; k < NeuronList.size(); k++) {
            Roi roi = new Roi(NeuronList.get(k));
            roi.setStrokeColor(Color.RED);
            ov.add(roi);
        }

        for (int k = 0; k < AstrocyteList.size(); k++) {
            Roi roi = new Roi(AstrocyteList.get(k));
            roi.setStrokeColor(Color.MAGENTA);
            ov.add(roi);
        }

        for (int k = 0; k < BackList.size(); k++) {
            Roi roi = new Roi(BackList.get(k));
            roi.setStrokeColor(Color.BLUE);
            ov.add(roi);
        }

        for (int k = 0; k < IgnoreList.size(); k++) {
            Roi roi = new Roi(IgnoreList.get(k));
            roi.setStrokeColor(Color.WHITE);
            ov.add(roi);
        }
        return ov;

    }

    private void exportAnnots(){

        System.out.println("export log...");

        String det_path = path + File.separator + "ann_" + inimg.getShortTitle() + ".log";

        PrintWriter logWriter = null;
        try {
            logWriter = new PrintWriter(det_path);
            logWriter.print("");
            logWriter.close();
        } catch (FileNotFoundException ex) {}

        try {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(det_path, true)));
            logWriter.println("# annotation output: TYPE x y w h");
        } catch (IOException e) {}

        for (int k = 0; k < NeuronList.size(); k++)
            logWriter.println("NEURON\t" +       IJ.d2s(NeuronList.get(k).getX(),0) + "\t" +         IJ.d2s(NeuronList.get(k).getY(),0) + "\t" +
                                                    IJ.d2s(NeuronList.get(k).getWidth(),0) + "\t" +     IJ.d2s(NeuronList.get(k).getHeight(),0));

        for (int k = 0; k < AstrocyteList.size(); k++)
            logWriter.println("ASTROCYTE\t" +       IJ.d2s(AstrocyteList.get(k).getX(),0) + "\t" +      IJ.d2s(AstrocyteList.get(k).getY(),0) + "\t" +
                                                    IJ.d2s(AstrocyteList.get(k).getWidth(),0) + "\t" +  IJ.d2s(AstrocyteList.get(k).getHeight(),0));

        for (int k = 0; k < BackList.size(); k++)
            logWriter.println("BACKGROUD\t" +       IJ.d2s(BackList.get(k).getX(),0) + "\t" +           IJ.d2s(BackList.get(k).getY(),0) + "\t" +
                                                    IJ.d2s(BackList.get(k).getWidth(),0) + "\t" +       IJ.d2s(BackList.get(k).getHeight(),0));

        for (int k = 0; k < IgnoreList.size(); k++)
            logWriter.println("IGNORE\t" +          IJ.d2s(IgnoreList.get(k).getX(),0) + "\t" +         IJ.d2s(IgnoreList.get(k).getY(),0) + "\t" +
                                                    IJ.d2s(IgnoreList.get(k).getWidth(),0) + "\t" +     IJ.d2s(IgnoreList.get(k).getHeight(),0));

        logWriter.close();
        System.out.println("done. "+det_path);

    }

    private double overlap(Rectangle rect1, Rectangle rect2) {

        double x11 = rect1.getX();//left -up
        double y11 = rect1.getY();//left -up

        double x12 = rect1.getX() + rect1.getWidth();//right -up

        double y12 = rect1.getY() + rect1.getHeight();//left-down

        double x21 = rect2.getX();//left -up
        double y21 = rect2.getY();//left -up

        double x22 = rect2.getX() + rect2.getWidth();//right -up

        double y22 = rect2.getY() + rect2.getHeight();//left-down

        double xDiff;
        if (x11 < x21) {
            xDiff = x12 - x21;
        } else {
            xDiff = x22 - x11;
        }

        double yDiff;
        if (y11 < y21) {
            yDiff = y12 - y21;
        } else {
            yDiff = y22 - y11;
        }
        xDiff = (xDiff < 0) ? 0 : xDiff;
        yDiff = (yDiff < 0) ? 0 : yDiff;

        return xDiff * yDiff;
    }

    public void mouseMoved(MouseEvent e) {
        currx = canvas.offScreenX(e.getX());
        curry = canvas.offScreenY(e.getY());
        PolygonRoi pr = new PolygonRoi(
                new float[]{currx - .5f * D, currx - .5f * D, currx + .5f * D, currx + .5f * D},
                new float[]{curry - .5f * D, curry + .5f * D, curry + .5f * D, curry - .5f * D},
                4,
                PolygonRoi.POLYGON
        );
        pr.setFillColor(new Color(1, 1, 0, 0.2f));
        if (floating_ovl.size()>0) floating_ovl.remove(floating_ovl.size() - 1);
//        floating_ovl.remove(pr);
        floating_ovl.add(pr);
        canvas.setOverlay(floating_ovl);
        inimg.updateAndDraw();
    }

    private Rectangle append(int x, int y, ArrayList<Rectangle> destination) {

        if (Math.round(x+(D/2))>=w || Math.round(y+(D/2))>=h) return null;

        int xRect = Math.round(x - D / 2f);
        int yRect = Math.round(y - D / 2f);

        if ((xRect < 0) || (yRect < 0)) return null;

        Rectangle rec = new Rectangle(xRect, yRect, D, D);

        // check if it overlaps with one of the background  or ignore patches - if so then don't add it
        // neurons and astrocytes can overlap and that one will be successfully added
        boolean overlaps = false; // useful if we got some back patches annotated
        for (int k = 0; k < BackList.size(); k++) {
            if (overlap(rec, BackList.get(k))>0) {
                overlaps = true;
                break;
            }
        }
        for (int k = 0; k < IgnoreList.size(); k++) {
            if (overlap(rec, IgnoreList.get(k))>0) {
                overlaps = true;
                break;
            }
        }

        if (overlaps) return null;

        destination.add(rec);

        return rec;

    }

    private void updateOverlay(Rectangle rec, Color col){

        Roi roi = new Roi(rec); // turn into ij roi that can be plotted
        roi.setStrokeColor(col);
        roi.setFillColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 40));

        Roi last = floating_ovl.get(floating_ovl.size() - 1);   // store the last
        floating_ovl.remove(floating_ovl.size() - 1);           // remove it
        floating_ovl.add(roi);                                  // add the new one
        floating_ovl.add(last);                                 // keep the floating square on top

        inimg.setOverlay(floating_ovl);
        inimg.updateAndDraw();

    }

    private void remove(int x, int y) {

        int xRect = Math.round(x - D / 2);
        int yRect = Math.round(y - D / 2);

        if ((xRect < 0) || (yRect < 0)) return;

        Rectangle rec = new Rectangle(xRect, yRect, D, D);

        // remove from the overlay, then  remove from the rectangle list (sequence is important here)

        for (int k = 0; k < NeuronList.size(); k++) {
            if (overlap(rec, NeuronList.get(k))>0) {
                floating_ovl.remove(new Roi(NeuronList.get(k)));
                NeuronList.remove(k);
            }
        }

        for (int k = 0; k < AstrocyteList.size(); k++) {
            if (overlap(rec, AstrocyteList.get(k))>0) {
                floating_ovl.remove(new Roi(AstrocyteList.get(k)));
                AstrocyteList.remove(k);
            }
        }

        for (int k = 0; k < BackList.size(); k++) {
            if (overlap(rec, BackList.get(k))>0) {
                floating_ovl.remove(new Roi(BackList.get(k)));
                BackList.remove(k);
            }
        }

        for (int k = 0; k < IgnoreList.size(); k++) {
            if (overlap(rec, IgnoreList.get(k))>0) {
                floating_ovl.remove(new Roi(IgnoreList.get(k)));
                IgnoreList.remove(k);
            }
        }


        inimg.setOverlay(floating_ovl);
        inimg.updateAndDraw();

    }

    private void generateObjectMap() {

        Arrays.fill(mask_obj, (byte) 0);

        for (int k = 0; k < NeuronList.size(); k++) {

            int xr = (int) Math.round(NeuronList.get(k).getX());
            int yr = (int) Math.round(NeuronList.get(k).getY());

            for (int xloop = xr; xloop < xr + D; xloop++) {
                for (int yloop = yr; yloop < yr + D; yloop++) {
                    mask_obj[yloop * w + xloop] = (byte) 255;
                }
            }
        }

        for (int k = 0; k < AstrocyteList.size(); k++) {

            int xr = (int) Math.round(AstrocyteList.get(k).getX());
            int yr = (int) Math.round(AstrocyteList.get(k).getY());

            for (int xloop = xr; xloop < xr + D; xloop++) {
                for (int yloop = yr; yloop < yr + D; yloop++) {
                    mask_obj[yloop * w + xloop] = (byte) 255;
                }
            }
        }

//        for (int k = 0; k < IgnoreList.size(); k++) {
//
//            int xr = (int) Math.round(IgnoreList.get(k).getX());
//            int yr = (int) Math.round(IgnoreList.get(k).getY());
//
//            for (int xloop = xr; xloop < xr + D; xloop++) {
//                for (int yloop = yr; yloop < yr + D; yloop++) {
//                    mask_obj[yloop * w + xloop] = (byte) 255;
//                }
//            }
//        }

    }

    private void print() {
        System.out.println("#patches:\t" + NeuronList.size() + " N,\t" + AstrocyteList.size() + " A,\t" + BackList.size() + " B,\t" + IgnoreList.size() + " I.");
    }

    private void exportPatches() {

        System.out.println("export patches...");

        File f;
        FileSaver fs = null;
        String filename;
        ImageStack collection;

        File f_root = new File(path);
        File f_n = new File(f_root.getAbsolutePath() + File.separator + "neuron");      f_n.mkdirs();
        File f_a = new File(f_root.getAbsolutePath() + File.separator + "astrocyte");   f_a.mkdirs();
        File f_b = new File(f_root.getAbsolutePath() + File.separator + "background");  f_b.mkdirs();

//        for(File file: f.listFiles()) file.delete(); // dont't delete, just overwirite as it exports

        collection = new ImageStack(D,D); // initialize
        int cnt_neurons = 0;
        for (int k = 0; k < NeuronList.size(); k++) {

            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
            ipCopy.setRoi(new Roi(NeuronList.get(k)));
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus("", ipCopy);
            fs = new FileSaver(impCopy);
            filename = f_n.getAbsolutePath() + File.separator + annotation_id+"_"+inimg.getShortTitle()+"_D"+IJ.d2s(NeuronList.get(k).getWidth(),0) + "_n" + String.format("%03d", k) + ".tif";
            fs.saveAsTiff(filename);
            System.out.println(filename);

            if (Math.round(NeuronList.get(k).getWidth())==D) {
                cnt_neurons++;
                collection.addSlice("n" + String.format("%03d", k) + "(" + IJ.d2s(NeuronList.get(k).getX(), 0) + "," + IJ.d2s(NeuronList.get(k).getY(), 0) + ")", impCopy.getProcessor());
            }


        }

        if (cnt_neurons>0) {
            fs = new FileSaver(new ImagePlus(inimg.getShortTitle(), collection));
            filename = path+ File.separator+inimg.getShortTitle()+"_neuron.tif";
            System.out.println("->" + filename);
            if (cnt_neurons>1) fs.saveAsTiffStack(filename); else fs.saveAsTiff(filename);
        }

        collection = new ImageStack(D,D);
        int cnt_astrocytes = 0;
        for (int k = 0; k < AstrocyteList.size(); k++) {

            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
            ipCopy.setRoi(new Roi(AstrocyteList.get(k)));
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus("", ipCopy);
            fs = new FileSaver(impCopy);
            filename = f_a.getAbsolutePath() + File.separator + annotation_id+"_"+inimg.getShortTitle()+"_D"+IJ.d2s(AstrocyteList.get(k).getWidth(),0) + "_a" + String.format("%03d", k) + ".tif";
            fs.saveAsTiff(filename);
            System.out.println(filename);

            if (Math.round(AstrocyteList.get(k).getWidth())==D) {
                cnt_astrocytes++;
                collection.addSlice("a" + String.format("%03d",k) + "("+IJ.d2s(AstrocyteList.get(k).getX(),0) + "," + IJ.d2s(AstrocyteList.get(k).getY(),0) + ")", impCopy.getProcessor());
            }

        }

        if (cnt_astrocytes>0) {
            fs = new FileSaver(new ImagePlus(inimg.getShortTitle(), collection));
            filename = path+ File.separator+inimg.getShortTitle()+"_astrocyte.tif";
            System.out.println("->" + filename);
            if (cnt_astrocytes>1) fs.saveAsTiffStack(filename); else fs.saveAsTiff(filename);
        }

        collection = new ImageStack(D,D);
        int cnt_backbround = 0;
        for (int k = 0; k < BackList.size(); k++) {

            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
            ipCopy.setRoi(new Roi(BackList.get(k)));
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus("", ipCopy);
            fs = new FileSaver(impCopy);
            filename = f_b.getAbsolutePath() + File.separator + annotation_id+"_"+inimg.getShortTitle()+"_D"+IJ.d2s(BackList.get(k).getWidth(),0) + "_b" + String.format("%03d", k) + ".tif";
            fs.saveAsTiff(filename);
            System.out.println(filename);

            if (Math.round(BackList.get(k).getWidth())==D){
                cnt_backbround++;
                collection.addSlice("b" + String.format("%03d",k) + "("+IJ.d2s(BackList.get(k).getX(),0)+ "," + IJ.d2s(BackList.get(k).getY(),0) + ")", impCopy.getProcessor());
            }

        }

        if (cnt_backbround>0){
            fs = new FileSaver(new ImagePlus(inimg.getShortTitle(), collection));
            filename = path+ File.separator+inimg.getShortTitle()+"_background.tif";
            System.out.println("->" + filename);
            if (cnt_backbround>1) fs.saveAsTiffStack(filename); else fs.saveAsTiff(filename);
        }

        String ann_path = path + File.separator + inimg.getShortTitle() + "_ann.tif";
        inimg.setOverlay(generateOverlay());
        fs = new FileSaver(inimg);
        fs.saveAsTiff(ann_path);

        System.out.println("->" + ann_path);

    }

    private void logic_or(byte[] a, byte[] b) { // final output is stored in a
        for (int k = 0; k < a.length; k++) {
            a[k] = ((a[k] & 0xff) > (b[k] & 0xff)) ? a[k] : b[k];
        }
    }

    public static String getFileExtension(String file_path) {
        String extension = "";

        int i = file_path.lastIndexOf('.');
        if (i >= 0) {
            extension = file_path.substring(i + 1);
        }

        return extension;
    }

    public void imageClosed(ImagePlus imagePlus) {

        if (canvas.getImage().getWindow()!=null)
            canvas.getImage().getWindow().removeKeyListener(this);
        if (canvas!=null)
            canvas.removeKeyListener(this);
        ImagePlus.removeImageListener(this);

        GenericDialog gd = new GenericDialog("Export?");
        gd.showDialog();
        if (gd.wasCanceled()) return;
        exportPatches();
        exportAnnots();

    }

    /////////////////////////////////////////////
    public void mouseClicked(MouseEvent e) {}

    public void mousePressed(MouseEvent e) {}

    public void mouseReleased(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}

    public void mouseDragged(MouseEvent e) {}

    public void keyPressed(KeyEvent e) {}

    public void keyReleased(KeyEvent e) {}

    public void imageOpened(ImagePlus imagePlus) {}

    public void imageUpdated(ImagePlus imagePlus) {}
}
