package patch;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;



/**
 *
 * @author Gadea
 */
/**
 * Created by miroslav on 26-2-15.
 *
 * read mosaic and select number of patches to extract for classification apply
 * classification by caling wndchrm command and grabbing the text output
 * ...well... the tsv part of the output with scores for each image from the
 * patch
 *
 * some hints on treating the output of the command
 *
 * If you want to print everything from "section B" to "section C" including
 * those lines,
 *
 * sed -ne '/^section B/,/^section/p'
 *
 * If you don't want to print the two "section" lines,
 *
 * sed -e '1,/^section B/d' -e '/^section/,$d'
 *
 * If you want to include "section B" and the closing parenthesis (but not
 * "section C"),
 *
 * sed -ne '/^section B/,/^)/p'
 *
 * command to extract part of the text output wndchrm test -r#0.75
 * ./train_m01_d50.fit | sed -e '1,/^image/d' -e '/^----------/,$d' wndchrm
 * classify ./directory_with_patches/
 *
 *
 */
public class MosaicClassify implements PlugIn {

    String command, fit_file_path, path, image_path, annot_file_path;
    double areaP = 30; //minimum porcentage of overlapping area
    double minArea = 0;

    @Override
    public void run(String string) {

        command = Prefs.get("annot.command", "wndchrm classify -r#1 -d50 ");
        fit_file_path = Prefs.get("annot.fit_file", System.getProperty("user.home"));
        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        image_path = Prefs.get("annot.image_path", System.getProperty("user.home"));
        annot_file_path = Prefs.get("annot.annot_path", System.getProperty("user.home"));

        GenericDialog gdG = new GenericDialog("MosaicClassify");
        gdG.addStringField("command", command, 80);
        gdG.addStringField("fit_path", fit_file_path, 80);
        gdG.addStringField("destination_dir", path, 80);
        gdG.addStringField("mosaic_path", image_path, 80);
        gdG.addStringField("annotation", annot_file_path, 80);
        gdG.addNumericField("minimum area of overlapping ", areaP, 0);

        command = gdG.getNextString();
        Prefs.set("annot.command", command);
        fit_file_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.fit_file", fit_file_path);
        path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.destination_folder", path);
        image_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.image_path", image_path);
        annot_file_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.annot_path", annot_file_path);
        areaP=gdG.getNextNumber();
        Prefs.set("annot.minArea", areaP);

        if (path.isEmpty() || fit_file_path.isEmpty() || annot_file_path.isEmpty() || image_path.isEmpty()) {
            return;
        }

        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }

        ImagePlus inimg = new ImagePlus(image_path);
        if (inimg == null) {
            return;
        }

        // variables to read from the command output
        ArrayList<String> tags = new ArrayList<String>();  // mosaic name
        ArrayList<Integer> xlocs = new ArrayList<Integer>(); //
        ArrayList<Integer> ylocs = new ArrayList<Integer>(); //
        ArrayList<Float> a_scrs = new ArrayList<Float>();   // astrocytes
        ArrayList<Float> n_scrs = new ArrayList<Float>();   // neurons
        ArrayList<Float> b_scrs = new ArrayList<Float>();   // background
        ArrayList<String> p_class = new ArrayList<String>(); //predict class
        ArrayList<Point> points = new ArrayList<Point>(); //(xlocs , ylocs)

        //to classify in Windows
        if (IJ.isWindows()) {
            //to test in Gadea's laptop (windows):
//            path = "C:\\Users\\Gadea\\Desktop\\GadeaT\\test";
//            fit_file_path = "C:\\Users\\Gadea\\Desktop\\GadeaT\\m01rd50.fit";
//            image_path = "C:\\Users\\Gadea\\Desktop\\GadeaT\\m01.tif";
//            annot_file_path = "C:\\Users\\Gadea\\Desktop\\GadeaT\\ann_m01.log";// "";// 
//            command = "C:\\Users\\Gadea\\Aplicaciones\\wndchrm\\wndchrm classify -r#1 -d50";
//            inimg = new ImagePlus(image_path);
            //----------

            System.out.println("wndchrm classify... ");
            String cmdWind = command + " " + new File(fit_file_path).getAbsolutePath() + " " + new File(path).getAbsolutePath();// + " C:\\Users\\Gadea\\Desktop\\GadeaT\\m01rd50.fit " + " C:\\Users\\Gadea\\Desktop\\GadeaT\\test";//
            System.out.println();
            System.out.println("***RUNNING***");
            System.out.println();
            System.out.println(cmdWind);
            System.out.println();
            System.out.println("***");
            System.out.println();
            String output = executeCommand(cmdWind);
            System.out.println("DONE!");

//        System.out.println(output);
            String[] readOutput = output.trim().split("\\n+");
            int n = 0;
            while (n < readOutput.length) {
                if (readOutput[n].startsWith("Classifying")) {
                    //read name image
                    String[] outNa = readOutput[n].trim().split("\\'");
                    String[] outName = outNa[1].trim().split("\\'");
                    String readName = removeExtension(new File(outName[0]).getName());

                    String[] comp = readName.trim().split("\\,+");
                    tags.add(comp[0]);
                    xlocs.add(Integer.valueOf(comp[5]));
                    ylocs.add(Integer.valueOf(comp[6]));
                    points.add(new Point(Integer.valueOf(comp[5]), Integer.valueOf(comp[6])));
                    n++;
                    if (readOutput[n].startsWith("astrocyte")) {
                        //read astrocyte scores
                        String[] outA = readOutput[n].trim().split(" ");
                        a_scrs.add((Float.valueOf(outA[1].trim())));
                        n++;
                    }
                    if (readOutput[n].startsWith("background")) {
                        //read background scores
                        String[] outB = readOutput[n].trim().split(" ");
                        b_scrs.add((Float.valueOf(outB[1].trim())));
                        n++;
                    }
                    if (readOutput[n].startsWith("neuron")) {
                        //read neuron scores
                        String[] outN = readOutput[n].trim().split(" ");
                        n_scrs.add((Float.valueOf(outN[1].trim())));
                        n++;
                    }
                    if (readOutput[n].startsWith("The resulting")) {
                        //read the predict value
                        String[] outV = readOutput[n].trim().split(":");
                        String[] outValue = outV[1].trim().split(" ");
                        p_class.add(outValue[0]);
                        n++;
                    }
                } else {
                    if (readOutput[n].startsWith("===")) {
                        break;
                    }
                    n++;
                }
            }
        } else if (IJ.isLinux()) {

            //************to classify************************
            System.out.println("wndchrm classify... ");
            // apply wndchrm classify to the patches
            // run terminal command and read output to String
            // print tht string
            // parse output
            // visualize
            String cmd = command + new File(fit_file_path).getAbsolutePath() + " " + new File(path).getAbsolutePath(); // -r#1 is redundant but...
//        command += " | sed -e '1,/^----------\nimage/d' -e '/^----------/,$d'"; // starts with keyword (---------\nimage) and ends with (----------)
            // not necessary to do sed as Java followup will take care of parsing the output

            // wndchrm classify -r#1 -d50 ./train_m01_d50.fit ./test_patches/    // example command
            System.out.println();
            System.out.println("***RUNNING***");
            System.out.println();
            System.out.println(cmd);
            System.out.println();
            System.out.println("***");
            System.out.println();

            String out = executeCommand(cmd);
            System.out.println("DONE!");
//        System.out.println(out);

            // read tab separated values
            boolean start = false;
            boolean stop = false;

            String[] readOut = out.trim().split("\\n+");
            for (int i = 0; i < readOut.length; i++) {
//            System.out.println(readOut[i]);
                if (readOut[i].startsWith("image\tnorm. fact.\tp(astrocyte)\tp(background)\tp(neuron)\tact. class\tpred. class\tpred. val.")) {// this is wndchrm output format
                    start = true;
//                System.out.println("START!");
                } else if (start == true && readOut[i].startsWith("----------")) {
                    stop = true;
//                System.out.println("STOP!");
                } else {
                    if (start == true && stop == false) {

                        String[] readLn = readOut[i].trim().split("\\s+");
                        String readName = removeExtension(new File(readLn[0].trim()).getName());
                        String[] comp = readName.trim().split("\\,+");

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
                        points.add(new Point(Integer.valueOf(comp[5]), Integer.valueOf(comp[6])));

                        a_scrs.add(Float.valueOf(readLn[2].trim()));
                        b_scrs.add(Float.valueOf(readLn[3].trim()));
                        n_scrs.add(Float.valueOf(readLn[4].trim()));
                        p_class.add(readLn[6].trim());//predict class
                    }
                }
            }
        }
        int D = getDPatches(path); //to get the dimensions of patches
        if (annot_file_path.isEmpty()) {

            // ******visualize the scores (neurons only for now)**********
            Overlay curr_ovl = new Overlay();// = inimg.getOverlay();

            for (int i = 0; i < xlocs.size(); i++) {

                Roi roi_to_add = new Roi(new Rectangle(xlocs.get(i), ylocs.get(i), D, D));
                roi_to_add.setFillColor(new Color(1, 0, 0, 0.3f * n_scrs.get(i)));
                roi_to_add.setStrokeColor(new Color(1, 0, 0, 1));
//            roi_to_add.setStrokeWidth(2);
                curr_ovl.add(roi_to_add);

            }

            ImagePlus imout = inimg.duplicate();
            imout.setOverlay(curr_ovl);
            imout.show();
            imout.updateAndDraw();

        } else {
            //*********** to evaluate**********

            int P = 0, total = 0, PClassify = 0, totalClassify = 0;
            //P is the number of neurons in annot
            //total is the number of patches in annot
            //PClassify is the number of neurons classified
            //totalClassify is the number of patches classified

            minArea = D * D * areaP / 100;

            //list with all rectangles from classification
            ArrayList<Rectangle> NeuronList = new ArrayList<Rectangle>();
            ArrayList<Rectangle> AstrocyteList = new ArrayList<Rectangle>();
            ArrayList<Rectangle> BackList = new ArrayList<Rectangle>();

            for (int i = 0; i < points.size(); i++) {
                String predict = p_class.get(i);
                if (predict.contains("neu")) {
                    NeuronList.add(new Rectangle((int) points.get(i).getX(), (int) points.get(i).getY(), D, D));
                    PClassify++;
                } else if (predict.contains("backg")) {
                    BackList.add(new Rectangle((int) points.get(i).getX(), (int) points.get(i).getY(), D, D));
                } else if (predict.contains("astro")) {
                    AstrocyteList.add(new Rectangle((int) points.get(i).getX(), (int) points.get(i).getY(), D, D));
                }
                totalClassify++;
            }

            //to read annot-file
            ArrayList<String> type_annot = new ArrayList<String>();  // mosaic name
            ArrayList<Point> points_annot = new ArrayList<Point>();
            //D is the same for all

            //to read annot file (.log)
            try {
                FileReader fr;
                BufferedReader br;
                String line;
                fr = new FileReader(annot_file_path);
                br = new BufferedReader(fr);

                br.readLine(); //to read the comment
                while ((line = br.readLine()) != null) {
                    String[] aux = line.split("\t", 5);//[0] TYPE, [1] x, [2] y, [3] D, [4] D
                    if (aux[0].contains("NEUR")) { //it only reads the neurons
                        type_annot.add(aux[0]);
                        points_annot.add(new Point(Integer.parseInt(aux[1]), Integer.parseInt(aux[2])));
                        P++;
                    }
                    total++;
                }
            } catch (IOException ex) {
                System.out.println("ERROR: " + ex.getMessage());
            }
            //only neurons from annot_.log
            Rectangle[] Neurons_annot = new Rectangle[points_annot.size()];

            for (int i = 0; i < points_annot.size(); i++) {
                String actual_class = type_annot.get(i);
                if (actual_class.contains("NEU")) {
                    Neurons_annot[i] = new Rectangle((int) points_annot.get(i).getX(), (int) points_annot.get(i).getY(), D, D);
                }
            }
            
            //to compare "neurons" (classification) with "neurons" (annot.log) 
            Rectangle[] NClass = new Rectangle[NeuronList.size()];
            Rectangle[] NAnnot = new Rectangle[NeuronList.size()];
            Double[] NOver = new Double[NeuronList.size()];

            for (int i = 0; i < NeuronList.size(); i++) {
                //maybe one neuron classified overloaps with 2 or more neurons from annot
                ArrayList<Rectangle> auxNeurons = new ArrayList<Rectangle>();
                ArrayList<Double> auxPorcOver = new ArrayList<Double>();
                NClass[i] = NeuronList.get(i);
                for (int j = 0; j < Neurons_annot.length; j++) {
                    double over = overlap(NeuronList.get(i), Neurons_annot[j]);
                    if (over > minArea) {
                        auxNeurons.add(Neurons_annot[j]);
                        auxPorcOver.add(over);
                    }
                }
                if (!auxNeurons.isEmpty()) {
                    double max = auxPorcOver.get(0);
                    NOver[i] = max;
                    NAnnot[i] = auxNeurons.get(0);
                    for (int j = 1; j < auxNeurons.size(); j++) {
                        if (max < auxPorcOver.get(j)) {
                            max = auxPorcOver.get(j);
                            NOver[i] = max;
                            NAnnot[i] = auxNeurons.get(j);
                        }
                    }
                } else {
                    NOver[i] = -1d;
                    NAnnot[i] = null;
                }

            }
            //here more than one neuron_annot may be repeated (different NClass have the same NAnnot)
            ArrayList<Rectangle> TP_neurons = new ArrayList<Rectangle>();
            ArrayList<Rectangle> FP_neurons = new ArrayList<Rectangle>();
            ArrayList<Rectangle> TP_annot_neurons = new ArrayList<Rectangle>();
            for (int i = 0; i < NAnnot.length; i++) {
                for (int j = 0; j < NAnnot.length; j++) {
                    if ((j != i) && (NAnnot[i] != null) && NAnnot[j] != null && (NAnnot[i] == NAnnot[j])) {
                        if (NOver[i] <= NOver[j]) {
                            NOver[i] = -1d;
                            NAnnot[i] = null;
                        } else {
                            NOver[j] = -1d;
                            NAnnot[j] = null;
                        }
                    }
                }
            }
            for (int i = 0; i < NClass.length; i++) {
                if (NOver[i] != -1d && NAnnot[i] != null) {
                    TP_neurons.add(NClass[i]);
                    TP_annot_neurons.add(NAnnot[i]);
                } else {
                    FP_neurons.add(NClass[i]);
                }
            }
            int TP, FN, FP;
            TP = TP_neurons.size();
            FP = FP_neurons.size();

            //FN is the number of neurons in annot minus the number of neurons found.
            FN = P - TP;

            System.out.println("total patches in annot: " + total);
            System.out.println("total patches in classification: " + totalClassify);
            System.out.println("P (Neurons) in annot: " + P);
            System.out.println("P' (Neurons) in classification: " + PClassify);
            System.out.println("TP: " + TP + " (in red)");
            System.out.println("FN: " + FN + " (in yellow)");
            System.out.println("FP: " + FP + " (in blue)");

            //to visualizate the results
            Overlay curr_ovl = new Overlay();
            for (Rectangle TP_neuron : TP_neurons) {
                //in red the patches are and have been classified like neurons
                Roi roi_to_add = new Roi(TP_neuron);
                roi_to_add.setFillColor(new Color(1, 0, 0, 0.2f));
                roi_to_add.setStrokeColor(new Color(1, 0, 0, 1));
                curr_ovl.add(roi_to_add);
            }
            for (Rectangle FP_neuron : FP_neurons) {
                //in blue the patches have been classified like neurons but they are not
                Roi roi_to_add = new Roi(FP_neuron);
                roi_to_add.setFillColor(new Color(0, 0, 1, 0.2f));
                roi_to_add.setStrokeColor(new Color(0, 0, 1, 1));
                curr_ovl.add(roi_to_add);
            }
            for (Rectangle TP_original : TP_annot_neurons) {
                //it paints the original patch in case of neuron
                Roi roi_to_add = new Roi(TP_original);
                roi_to_add.setStrokeColor(Color.WHITE);
                curr_ovl.add(roi_to_add);
            }

            ImagePlus imout = inimg.duplicate();
            imout.setOverlay(curr_ovl);
            imout.show();
            imout.updateAndDraw();
        }

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

    private String executeCommand(String command) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader
                    = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
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
        if (extensionIndex == -1) {
            return filename;
        }

        return filename.substring(0, extensionIndex);
    }

    protected int getDPatches(String path) {
        int count = 0;
        int D = 0;
        File directory = new File(path);
        String[] filesDir = directory.list();
        int nFiles = filesDir.length;
        if (nFiles != 0) {
            while (!filesDir[count].contains(".tif") && count < filesDir.length) {
                count++;
            }
            if (count < filesDir.length) {
                ImagePlus img = new ImagePlus(directory.getPath() + "/" + filesDir[count]);
                D = img.getWidth();
            }
        }
        return D;
    }
}
