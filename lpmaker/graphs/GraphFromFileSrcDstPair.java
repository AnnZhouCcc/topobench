package lpmaker.graphs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphFromFileSrcDstPair extends Graph {

	public GraphFromFileSrcDstPair(int size, String fileName, int totalPorts, boolean shouldWriteAdjacencyList){
		super(size); // size: numSwitches
		numPorts = totalPorts;
		populateAdjacencyList(fileName);
		name="fromfile";
		if (shouldWriteAdjacencyList) writeAdjacencyList();
	}

	/*
	 * Construction of the graph give a degree and noNodes and input file
	 */

	private void populateAdjacencyList(String fName){
		// INPUT: [source switch]->[destination switch]
		String regex = "(\\d*)->(\\d*)";
		try {
			BufferedReader br = new BufferedReader(new FileReader(fName));
			String strLine = "";
			while ((strLine = br.readLine()) != null){
				StringTokenizer strTok = new StringTokenizer(strLine);
				String token = strTok.nextToken();
				Pattern pattern = Pattern.compile(regex);
				Matcher matcher = pattern.matcher(token);
				if (matcher.find() && matcher.groupCount()==2) {
					int source_sw = Integer.parseInt(matcher.group(1));
					int destination_sw = Integer.parseInt(matcher.group(2));
					if (!isNeighbor(source_sw, destination_sw)) addBidirNeighbor(source_sw, destination_sw);
				} else {
					System.out.println("Pattern is not matched.");
					System.out.println("The input format needs to be: [source switch]->[destination switch]");
				}
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// add servers to switches in proportion to degree for scale-free stuff
		setUpFixWeight(0);
		for(int t = 0; t < noNodes; t++){
			int curr_weight = (numPorts - adjacencyList[t].size()); // total number of ports = number of servers + number of neighboring switches
			weightEachNode[t] = curr_weight;
			if(t == 0){
				weightBeforeEachNode[t] = 0;
			}
			weightBeforeEachNode[t+1] = curr_weight + weightBeforeEachNode[t];
			totalWeight += curr_weight;
		}
		System.out.println("total number of servers = " + totalWeight);
	}

	public int svrToSwitch(int i) {
		int curr_total = 0;
		for (int sw = 0; sw < noNodes; sw++) {
			int num_here = weightEachNode[sw];
			if (curr_total + num_here > i) return sw;
			else curr_total += num_here;
		}
		System.out.println("Failed to locate the server on any switch.");
		System.out.println("server id = " + i + ", number of switches = " + noNodes + ", number of ports = " + numPorts);
		return -1;
	}

	public int[] getServersForSwitch(int whichSwitch) {
		int startServer = 0;
		for (int sw=0; sw<noNodes; sw++) {
			if (sw < whichSwitch) {
				startServer += weightEachNode[sw];
			}
		}

		int numServers = weightEachNode[whichSwitch];
		int[] serverarr = new int[numServers];
		for (int s=0; s<numServers; s++) {
			serverarr[s] = s+startServer;
		}
		return serverarr;
	}

	public void writeAdjacencyList() {
		String writefile = "intermediatefiles/adjacencylist.txt";
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(writefile));
			for (int u = 0; u < noNodes; u++) {
				int size = adjacencyList[u].size();
				out.write(size + "\t");
				for (int j = 0; j < size; j++) {
					int v = adjacencyList[u].get(j).linkTo;
					out.write(v + "\t");
				}
				out.write("\n");
			}
			out.close();
		} catch (Exception e) {
			System.err.println("GraphFromFileSrcDstPair writeAdjacencyList Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
