/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Sift;

/**
 *
 * @author Gadea
 */
public class InstHistograms {

    Integer[] histogram;
    String name = "";
    int cl = -1;
    int cluster = -1;

    public InstHistograms(Integer[] histogram, String name, int cl, int cluster) {
        this.histogram = histogram;
        this.name = name;
        this.cl = cl;
        this.cluster = cluster;
    }

    public InstHistograms(Integer[] histogram, String name, int cl) {
        this.histogram = histogram;
        this.name = name;
        this.cl = cl;
        this.cluster = -1;
    }

    public Integer[] getHistogram() {
        return histogram;
    }

    public String getName() {
        return name;
    }

    public int getCl() {
        return cl;
    }

    public int getCluster() {
        return cluster;
    }

    public void setHistogram(Integer[] histogram) {
        this.histogram = histogram;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCl(int cl) {
        this.cl = cl;
    }

    public void setCluster(int cluster) {
        this.cluster = cluster;
    }

}
