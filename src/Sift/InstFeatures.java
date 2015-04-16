/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Sift;

import java.util.Vector;
import mpi.cbg.fly.Feature;

/**
 *
 * @author Gadea
 */
public class InstFeatures {
     Vector< Feature> fs1;
     int cl;
     String name;
     
     public InstFeatures(){
         fs1= new Vector<Feature>();
         cl=-1;
         name="";
         
     }
     public InstFeatures(Vector<Feature> fs1, int cl, String name)
     {
         this.fs1=fs1;
         this.cl=cl;
         this.name=name;
     }
     public InstFeatures(Vector<Feature> fs1, int cl)
     {
         this.fs1=fs1;
         this.cl=cl;
         this.name="";
     }

    public Vector<Feature> getFs1() {
        return fs1;
    }

    public int getCl() {
        return cl;
    }
    
    public String getName()
    {
        return name;
    }
     
}
