/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package patch;

import java.util.Vector;
import mpi.cbg.fly.Feature;

/**
 *
 * @author Gadea
 */
public class instFeatures {
     Vector< Feature> fs1;
     int cl;
     
     public instFeatures(){
         fs1= new Vector<Feature>();
         cl=-1;
     }
     public instFeatures(Vector<Feature> fs1, int cl)
     {
         this.fs1=fs1;
         this.cl=cl;
     }

    public Vector<Feature> getFs1() {
        return fs1;
    }

    public int getCl() {
        return cl;
    }
     
}
