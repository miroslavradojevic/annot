/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Sift;

import mpi.cbg.fly.Feature;

/**
 *
 * @author Gadea
 */
public class OneFeature {

    private Feature f;
    private int cl=-1;
    private String name="";
    private int cluster=-1;

    public OneFeature(Feature f, int cl, String name) {
        this.f = f;
        this.cl = cl;
        this.name = name;
        this.cluster = -1;
    }

    public OneFeature(Feature f, int cl, String name, int cluster) {
        this.f = f;
        this.cl = cl;
        this.name = name;
        this.cluster = cluster;
    }

    public int getCluster() {
        return cluster;
    }

    public void setF(Feature f) {
        this.f = f;
    }

    public void setCl(int cl) {
        this.cl = cl;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCluster(int cluster) {
        this.cluster = cluster;
    }

    public Feature getF() {
        return f;
    }

    public int getCl() {
        return cl;
    }

    public String getName() {
        return name;
    }
}
