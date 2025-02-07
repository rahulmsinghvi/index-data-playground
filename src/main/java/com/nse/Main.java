package com.nse;

public class Main {

    public static void main(String[] args) throws Exception {
        IndexPlayground indexPlayground = new IndexPlayground();
        indexPlayground.readCsvFile("/Users/rmahendrakumar/Downloads/nifty_index_playbook_addn.csv");
    }
}