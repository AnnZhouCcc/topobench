/* *******************************************************
 * Released under the MIT License (MIT) --- see ../LICENSE
 * Copyright (c) 2014 Ankit Singla, Sangeetha Abdu Jyothi, Chi-Yao Hong, Lucian Popa, P. Brighten Godfrey, Alexandra Kolla
 * ******************************************************** */

/* The main class takes as inputs various parameters and constructs the desired graph type with
   its specified parameters. The Graph class has methods to print the linear program with the 
   specified graph type and the specified traffic matrix. You may add your own traffic matrices
   by following the example of All-to-all etc. in that file (Graph.java).
 */


package lpmaker;

import java.util.*;
import java.io.*;

import lpmaker.graphs.*;

public class ProduceLPMultipleTM {
	public static int switches = 80; 	// # of switch
	public static int switchports = 8; 	// # of ports per switch
	public static int serverports = 1; 	// # of ports per switch that used to connect to servers
	public static int extended_switches = 0; // # switches added in expansion
	public static int nsvrs = 0;	// # of servers we want to support (this, if non-zero, overrides the server ports arguments.)

	public static void main(String args[]) throws IOException{
		// Read graph from file, print linear program for both that graph, and equivalent random graph for comparison; graph file has lines of type "<source switch>-><destination switch>"
		switches = 80;
		switchports = 64;
		String graphFile = "graphfiles/rrg_instance1_80_64.edgelist";
		System.out.println("number of switches = " + switches + ", number of switch ports = " + switchports);
		System.out.println("graph file: " + graphFile);
		GraphFromFileSrcDstPair mynet = new GraphFromFileSrcDstPair(switches, graphFile, switchports, true);

		int c = 2;
		int runs = 0;	// Now used as random seed
		TrafficMatrix tm1 = new TrafficMatrix(switches, c, runs);
		System.out.println("PrintGraphforMCFFairCondensed");
		mynet.PrintGraphforMCFFairCondensed("my." + runs + ".lp",100,tm1.switchLevelMatrix);

		runs++;
		TrafficMatrix tm2 = new TrafficMatrix(switches, c, runs);
		System.out.println("PrintGraphforMCFFairCondensed");
		mynet.PrintGraphforMCFFairCondensed("my." + runs + ".lp",100,tm2.switchLevelMatrix);

		runs++;
		double[][][] matrixSeries = {tm1.switchLevelMatrix,tm2.switchLevelMatrix};
		System.out.println("PrintLPMultipleTM");
		mynet.PrintLPMultipleTM("my." + runs + ".lp", matrixSeries);
	}
}