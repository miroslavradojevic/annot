package patch;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by miroslav on 26-2-15.
 *
 * read mosaic and select number of patches to extract for classification
 * apply classification by caling wndchrm command and grabbing the text output
 * ...well... the tsv part of the output with scores for each image from the patch
 *
 * some hints on treating the output of the command
 *
 * If you want to print everything from "section B" to "section C" including those lines,

 sed -ne '/^section B/,/^section/p'

 * If you don't want to print the two "section" lines,

 sed -e '1,/^section B/d' -e '/^section/,$d'

 * If you want to include "section B" and the closing parenthesis (but not "section C"),

 sed -ne '/^section B/,/^)/p'

 * command to extract part of the text output
 wndchrm test -r#0.75 ./train_m01_d50.fit | sed -e '1,/^image/d' -e '/^----------/,$d'
 wndchrm classify ./directory_with_patches/

 *
 */
public class MosaicDetection implements PlugIn {

    ImagePlus inimg;
    String path, path1, fit_file_path;
    int D, N;
    int W, H;
    String ptch_distribution;

    public void run(String s) {

        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("annot.D", 400); // annot. square size
        N = (int) Prefs.get("annot.N", 100);
        fit_file_path = Prefs.get("annot.fit_file", System.getProperty("user.home"));

        GenericDialog gdG = new GenericDialog("MosaicDetection");
        gdG.addStringField("destination_folder", path, 80);
        gdG.addNumericField("square_size", D, 0);
        gdG.addNumericField("nr_test_patches", N, 0);
        gdG.addChoice("patch_distribution", new String[]{"GRID", "RANDOM"}, "GRID");
        gdG.addMessage("----------------------------");
        gdG.addStringField("fit_file", fit_file_path, 80);

        gdG.showDialog();
        if (gdG.wasCanceled()) return;

        path = new File(gdG.getNextString()).getAbsolutePath(); Prefs.set("annot.destination_folder", path);
        D = (int) gdG.getNextNumber();                          Prefs.set("annot.D", D);
        N = (int) gdG.getNextNumber();                          Prefs.set("annot.N", N);
        ptch_distribution = gdG.getNextChoice();
        fit_file_path = new File(gdG.getNextString()).getAbsolutePath(); Prefs.set("annot.fit_file", fit_file_path);

        if (path.isEmpty() || fit_file_path.isEmpty()) return;

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
        if (!Annotationer.getFileExtension(inimg.getTitle()).equalsIgnoreCase("TIF")) {
            IJ.log("open image with .TIF extension");
            return;
        }

        File f = new File(path);
        if (!f.exists()) f.mkdirs();
        path1 = f.getAbsolutePath() + File.separator + "test_patches"; //

        f = new File(path1);
        if (!f.exists()) f.mkdirs();

        inimg.show();
        W = inimg.getWidth();
        H = inimg.getHeight();

        Overlay ov = new Overlay();
        FileSaver fs;

        // check the sampling model
        if (ptch_distribution.equals("GRID")) {

            int margin = D/2;
            int step = (int) Math.floor(Math.sqrt(( (W-D) * (H-D) ) / N));

            int count = 0;

            for (int x = margin; x < W-margin-D; x+=step) {
                for (int y = margin; y < H-margin-D; y+=step) {

                    Rectangle rec = new Rectangle(x, y, D, D);
                    Roi rec_roi = new Roi(rec);
                    ov.add(rec_roi);

                    inimg.setOverlay(ov);
                    inimg.updateAndDraw();

                    // extract the patch
                    ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
                    ipCopy.setRoi(rec_roi);
                    ipCopy = ipCopy.crop();
                    ImagePlus impCopy = new ImagePlus("", ipCopy);
                    fs = new FileSaver(impCopy);
                    String filename = f.getAbsolutePath() + File.separator + inimg.getShortTitle() +
                            ",X,Y,D,i," + IJ.d2s(x,0) + "," + IJ.d2s(y,0) + "," + IJ.d2s(D,0) + "," + IJ.d2s(count,0)+".tif";

                    fs.saveAsTiff(filename);

                    count++;
                    System.out.println(filename);


                }
            }

        }
        else if (ptch_distribution.equals("RANDOM")) {

//            System.out.println("random...");

        }



        /*


        END HERE THE  FIRST PART



         */


        /*

        REMAINDER IS FOR THE NEXT PLUGIN THAT READS THE CLASSIFICATION AND CREATES THE MAP AND EVALUATES IF GROUND TRUTH WAS GIVEN

         */
//        inimg.setOverlay(ov);
//        inimg.updateAndDraw();

        System.out.println("wndchrm classify... " + f.getAbsolutePath());
        // apply wndchrm classify to the patches
        // run terminal command and read output to String
        // print tht string
        // parse output
        // visualize
        String command = "wndchrm classify " + " -r#1 " + " -d50 " + new File(fit_file_path).getAbsolutePath() + " " + f.getAbsolutePath(); // -r#1 is redundant but...
//        command += " | sed -e '1,/^----------\nimage/d' -e '/^----------/,$d'"; // starts with keyword (---------\nimage) and ends with (----------)
        // not necessary to do sed as Java followup will take care of parsing the output

        // wndchrm classify -r#1 -d50 ./train_m01_d50.fit ./test_patches/    // example command
        System.out.println();
        System.out.println("***RUNNING***");
        System.out.println();
        System.out.println(command);
        System.out.println();
        System.out.println("***");
        System.out.println();

        String out = executeCommand(command);
        System.out.println("DONE!");
//        System.out.println(out);



        // variables to read from the command output
        ArrayList<String>   tags    = new ArrayList<String>();  // mosaic name
        ArrayList<Integer>  xlocs   = new ArrayList<Integer>(); //
        ArrayList<Integer>  ylocs   = new ArrayList<Integer>(); //
        ArrayList<Float>    a_scrs  = new ArrayList<Float>();   // astrocytes
        ArrayList<Float>    n_scrs  = new ArrayList<Float>();   // neurons
        ArrayList<Float>    b_scrs  = new ArrayList<Float>();   // background


        // read tab separated values
        boolean start = false;
        boolean stop  = false;

        String[] 	readOut      = 	out.trim().split("\\n+");
        for (int i = 0; i < readOut.length; i++) {

//            System.out.println(readOut[i]);
            if (readOut[i].startsWith("image\tnorm. fact.\tp(astrocyte)\tp(background)\tp(neuron)\tact. class\tpred. class\tpred. val.")) {// this is wndchrm output format
                start = true;
//                System.out.println("START!");
//
            }
            else if (start==true && readOut[i].startsWith("----------")) {
                stop = true;
//                System.out.println("STOP!");
            }
            else {
                if (start==true && stop==false) {

                    String[] 	readLn      = 	readOut[i].trim().split("\\s+");
                    String      readName    =   removeExtension(new File(readLn[0].trim()).getName());

                    String[]    comp        = readName.trim().split("\\,+");

//                    System.out.println(
//                            readOut[i] + "\n\n" +
//                            "--->\t" + comp[0] + "\t" +
//                                       comp[5] + "\t" +
//                                       comp[6] + "\t" +
//                                    readLn[2].trim() + "\t" +
//                                    readLn[3].trim() + "\t" +
//                                    readLn[4].trim());

                    tags.add(comp[0]);
                    xlocs.add(Integer.valueOf(comp[5]));
                    ylocs.add(Integer.valueOf(comp[6]));

                    a_scrs.add(Float.valueOf(readLn[2].trim()));
                    b_scrs.add(Float.valueOf(readLn[3].trim()));
                    n_scrs.add(Float.valueOf(readLn[4].trim()));

                }
            }

//            System.out.println(readOut[i]);
//            System.out.println("line " + i);

        }

        // visualize the scores (neurons only for now)
        Overlay curr_ovl = new Overlay();// = inimg.getOverlay();

        for (int i = 0; i < xlocs.size(); i++) {

            Roi roi_to_add = new Roi(new Rectangle(xlocs.get(i), ylocs.get(i), D, D));
            roi_to_add.setFillColor(new Color(1, 0, 0, 0.3f*n_scrs.get(i)));
            roi_to_add.setStrokeColor(new Color(1, 0, 0, 1));
//            roi_to_add.setStrokeWidth(2);
            curr_ovl.add(roi_to_add);

        }

        ImagePlus imout = inimg.duplicate();
        imout.setOverlay(curr_ovl);
        imout.show();
        imout.updateAndDraw();

    }

    private String executeCommand(String command) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine())!= null) {
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return output.toString();

    }

    public static String removeExtension(String s) {

        String separator = File.separator;//System.getProperty("file.separator");
        String filename;

        // Remove the path upto the filename.
        int lastSeparatorIndex = s.lastIndexOf(separator);
        if (lastSeparatorIndex == -1) {
            filename = s;
        } else {
            filename = s.substring(lastSeparatorIndex + 1);
        }

        // Remove the extension.
        int extensionIndex = filename.lastIndexOf(".");
        if (extensionIndex == -1)
            return filename;

        return filename.substring(0, extensionIndex);
    }

}
