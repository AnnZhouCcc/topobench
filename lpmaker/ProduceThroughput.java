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

import lpmaker.graphs.Graph;
import lpmaker.graphs.GraphFromFileSrcDstPair;
import lpmaker.graphs.LeafSpine;
import lpmaker.graphs.ServerGraphFromFileSrcDstPair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.StringTokenizer;

public class ProduceThroughput {

	private static void initializeServerPattern(double[] pattern) {
		for (int i=0; i<pattern.length; i++) {
			pattern[i] = -1;
		}
	}

	private static void setServerPattern(double[] newPattern, int npStart, int npEnd, double[] oldArray, int oaStart, int oaEnd) {
		if ((npEnd-npStart) != (oaEnd-oaStart)) {
			System.out.println("***Error: setServerPattern length mismatch, npStart="+npStart+", npEnd="+npEnd+", oaStart="+oaStart+", oaEnd="+oaEnd);
		}
		for (int i=0; i<npEnd-npStart; i++) {
			oldArray[i+oaStart] = newPattern[i+npStart];
		}
	}

	private static boolean isServerPatternIdentical(double[] pattern1, double[] pattern2) {
		if (pattern1.length != pattern2.length) return false;
		for (int i=0; i<pattern1.length; i++) {
			if (pattern1[i] != pattern2[i]) return false;
		}
		return true;
	}

	private static boolean isServerPatternSet(double[] pattern) {
		boolean isSet = false;
		for (int i=0; i<pattern.length; i++) {
			if (pattern[i] != -1) isSet = true;
		}
		return isSet;
	}

	public static void main(String args[]) {
		// Set parameters
		int linkcapacity = 1;
		double maxFlow = 100;

		// Parse arguments
		int numLeafSwitches = Integer.parseInt(args[0]);
		int numSpineSwitches = Integer.parseInt(args[1]);
		int numServers = Integer.parseInt(args[2]);
		int numPorts = Integer.parseInt(args[3]);
		String tag = args[4];

		int numSwitches = numLeafSwitches + numSpineSwitches;
		int numNodes = numServers + numSwitches;
		String networkthroughputfile = "intermediatefiles/networkthroughput";
		String adjacencylistfile = "intermediatefiles/adjacencylist.txt";
		String serverlevelmatrixfile = "intermediatefiles/serverlevelmatrix.txt";
		String writefile = "intermediatefiles/throughput" + tag + ".txt";

		ArrayList[] adjacencyList = new ArrayList[numSwitches];
		double[][] serverLevelMatrix = new double[numServers][numServers];
		ArrayList<Integer>[] serverList = new ArrayList[numSwitches];

		try {
			BufferedReader reader = new BufferedReader(new FileReader(networkthroughputfile));
			String line = reader.readLine();
			double networkthroughput = Double.parseDouble(line);
			System.out.println("network throughput=" + networkthroughput);
			reader.close();

			reader = new BufferedReader(new FileReader(adjacencylistfile));
			line = "";
			int switchnumber = 0;
			while ((line = reader.readLine()) != null) {
				ArrayList<Integer> list = new ArrayList<>();
				StringTokenizer tokenizer = new StringTokenizer(line);
				int numNeighbors = Integer.parseInt(tokenizer.nextToken());
				for (int i=0; i<numNeighbors; i++) {
					list.add(Integer.parseInt(tokenizer.nextToken()));
				}
				adjacencyList[switchnumber] = list;
				switchnumber++;
			}
			reader.close();

			reader = new BufferedReader(new FileReader(serverlevelmatrixfile));
			line = "";
			int from = 0;
			while ((line = reader.readLine()) != null) {
				StringTokenizer tokenizer = new StringTokenizer(line);
				for (int to=0; to<numServers; to++) {
					serverLevelMatrix[from][to] = Double.parseDouble(tokenizer.nextToken());
				}
				from++;
			}
			if (from != numServers) {
				System.out.println("***Error: from != numServers, from=" + from + ", numServers=" + numServers);
			}
			reader.close();

			int currServerSum = 0;
			for (int sw=0; sw<numSwitches; sw++) {
				int numServersThisSwitch = numPorts - adjacencyList[sw].size();
				ArrayList<Integer> list = new ArrayList<>();
				for (int svr=0; svr<numServersThisSwitch; svr++) {
					list.add(svr+currServerSum);
				}
				currServerSum += numServersThisSwitch;
				serverList[sw] = list;
			}
			if (currServerSum != numServers) {
				System.out.println("***Error: currServerSum != numServers, currServerSum="+currServerSum+", numServers="+numServers);
			}

			double server_throughput = maxFlow;
			for (int sw=0; sw<numSwitches; sw++) {
				HashSet<Integer> group1 = new HashSet<>();
				HashSet<Integer> group2 = new HashSet<>();
				double[] group1pattern = new double[numServers * 2];
				double[] group2pattern = new double[numServers * 2];
				initializeServerPattern(group1pattern);
				initializeServerPattern(group2pattern);

				for (int svr : serverList[sw]) {
					double[] serverpattern = new double[numServers * 2];
					setServerPattern(serverLevelMatrix[svr], 0, numServers, serverpattern, 0, numServers);
					double[] serverpatterncolumn = new double[numServers];
					for (int i = 0; i < numServers; i++) {
						serverpatterncolumn[i] = serverLevelMatrix[i][svr];
					}
					setServerPattern(serverpatterncolumn, 0, numServers, serverpattern, numServers, numServers * 2);

					if (!isServerPatternSet(group1pattern)) {
						setServerPattern(serverpattern, 0, numServers * 2, group1pattern, 0, numServers * 2);
						group1.add(svr);
					} else {
						if (isServerPatternIdentical(serverpattern, group1pattern)) {
							group1.add(svr);
						} else {
							if (!isServerPatternSet(group2pattern)) {
								setServerPattern(serverpattern, 0, numServers * 2, group2pattern, 0, numServers * 2);
								group2.add(svr);
							} else {
								if (isServerPatternIdentical(serverpattern, group2pattern)) {
									group2.add(svr);
								} else {
									System.out.println("***Error: a third serverpattern occurs for switch " + sw);
								}
							}
						}
					}
				}

				double outgoingtraffic1 = 0, outgoingtraffic2 = 0, incomingtraffic1 = 0, incomingtraffic2 = 0, outgoingcapacity1 = 0, outgoingcapacity2 = 0, incomingcapacity1 = 0, incomingcapacity2 = 0;
				for (int i = 0; i < numServers; i++) {
					outgoingtraffic1 += group1pattern[i];
					outgoingtraffic2 += group2pattern[i];
					incomingtraffic1 += group1pattern[i + numServers];
					incomingtraffic2 += group2pattern[i + numServers];
				}
				outgoingtraffic1 *= group1.size();
				incomingtraffic1 *= group1.size();
				outgoingtraffic2 *= group2.size();
				incomingtraffic2 *= group2.size();

				outgoingcapacity1 = group1.size() * linkcapacity;
				incomingcapacity1 = group1.size() * linkcapacity;
				outgoingcapacity2 = group2.size() * linkcapacity;
				incomingcapacity2 = group2.size() * linkcapacity;

				double outgoingthroughput1 = outgoingtraffic1 == 0 ? maxFlow : outgoingcapacity1 / outgoingtraffic1;
				double outgoingthroughput2 = outgoingtraffic2 == 0 ? maxFlow : outgoingcapacity2 / outgoingtraffic2;
				double incomingthroughput1 = incomingtraffic1 == 0 ? maxFlow : incomingcapacity1 / incomingtraffic1;
				double incomingthroughput2 = incomingtraffic2 == 0 ? maxFlow : incomingcapacity2 / incomingtraffic2;

				server_throughput = Math.min(server_throughput, outgoingthroughput1);
				server_throughput = Math.min(server_throughput, outgoingthroughput2);
				server_throughput = Math.min(server_throughput, incomingthroughput1);
				server_throughput = Math.min(server_throughput, incomingthroughput2);
			}

			BufferedWriter out = new BufferedWriter(new FileWriter(writefile,true));
			out.write(networkthroughput + "\t" + server_throughput + "\t" + Math.min(networkthroughput,server_throughput) + "\n");
			out.close();
		} catch (Exception e) {
			System.err.println("ProduceThroughput Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

}
