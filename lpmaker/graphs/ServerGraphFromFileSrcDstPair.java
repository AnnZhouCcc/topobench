package lpmaker.graphs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerGraphFromFileSrcDstPair extends Graph {

	int numServers;
	int numSwitches;

	public ServerGraphFromFileSrcDstPair(int _numServers, int _numSwitches, String fileName, int totalPorts){
		// noNodes is number of servers + number of switches for this topology.
		super(_numServers+_numSwitches);
		numServers = _numServers;
		numSwitches = _numSwitches;
		numPorts = totalPorts;
		populateAdjacencyList(fileName);
		checkAdjacencyList();
		name="fromfile";
	}

	private void printAdjacencyList() {
		for (int i=0; i<5; i++) {
			System.out.print(i + ": ");
			for (int j=0; j<adjacencyList[i].size(); j++) {
				System.out.print(adjacencyList[i].get(j).linkTo + " ");
			}
			System.out.println();
		}

		for (int i=3072; i<3077; i++) {
			System.out.print(i + ": ");
			for (int j=0; j<adjacencyList[i].size(); j++) {
				System.out.print(adjacencyList[i].get(j).linkTo + " ");
			}
			System.out.println();
		}
	}

	/*
	 * Construction of the graph give a degree and noNodes and input file
	 */

	private void populateAdjacencyList(String fName){
		// INPUT: [source switch]->[destination switch]
		int switchOffset = numServers;
		String regex = "(\\d*)->(\\d*)";
		try {
			// Add SW-SW links
			BufferedReader br = new BufferedReader(new FileReader(fName));
			String strLine = "";
			while ((strLine = br.readLine()) != null){
				StringTokenizer strTok = new StringTokenizer(strLine);
				String token = strTok.nextToken();
				Pattern pattern = Pattern.compile(regex);
				Matcher matcher = pattern.matcher(token);
				if (matcher.find() && matcher.groupCount()==2) {
					int source_sw = Integer.parseInt(matcher.group(1))+switchOffset;
					int destination_sw = Integer.parseInt(matcher.group(2))+switchOffset;
					if (!isNeighbor(source_sw, destination_sw)) addBidirNeighbor(source_sw, destination_sw);
				} else {
					System.out.println("Pattern is not matched.");
					System.out.println("The input format needs to be: [source switch]->[destination switch]");
				}
			}
			br.close();

			// Give up on using weightBeforeEachNode.
			// For weightEachNode, only the last 80 entries are useful.
			setUpFixWeight(0);
			for(int t = switchOffset; t < switchOffset+numSwitches; t++){
				int curr_weight = (numPorts - adjacencyList[t].size()); // total number of ports = number of servers + number of neighboring switches
				weightEachNode[t] = curr_weight;
			}
			totalWeight = numServers;
			System.out.println("total number of servers = " + totalWeight);

			// Add SW-SVR links
			int sumServers = 0;
			for (int sw=switchOffset; sw<switchOffset+numSwitches; sw++) {
				int weight = weightEachNode[sw];
				for (int w=0; w<weight; w++) {
					int svr = sumServers + w;
					addBidirNeighbor(sw, svr);
				}
				sumServers += weight;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void checkAdjacencyList() {
		for (int i=0; i<numServers; i++) {
			if (adjacencyList[i].size() != 1) {
				System.out.println("**Error: server node has degree not 1: degree=" + adjacencyList[i].size());
			}
		}
		for (int i=numServers; i<noNodes; i++) {
			for (int j = 0; j < adjacencyList[i].size(); j++) {
				int v = adjacencyList[i].get(j).linkTo;
				if (!adjacencyList[v].contains(i)) {
					System.out.println("**Error: link is not bi-directional: linkfrom=" + i + ",linkto=" + v);
				}
			}
			if (adjacencyList[i].size() != numPorts) {
				System.out.println("**Error: switch node has degree not numPorts: degree=" + adjacencyList[i].size() + ",numPorts=" + numPorts);
			}
		}
	}

//	public int svrToSwitch(int i) {
//		if (i>=numServers) {
//			System.out.println("**Error: whichserver exceeds total number of servers in svrToSwitch: whichserver=" + i + ", numservers=" + numServers);
//			System.exit(0);
//		}
//
//		int switchOffset = numServers;
//		int sumServers = 0;
//		for (int sw = switchOffset; sw < switchOffset+numSwitches; sw++) {
//			sumServers += weightEachNode[sw];
//			if (sumServers > i) {
//				return sw;
//			}
//		}
//		System.out.println("Failed to locate the server on any switch.");
//		System.out.println("server id = " + i + ", number of switches = " + numSwitches + ", number of ports = " + numPorts);
//		return -1;
//	}
//
//	public int[] getServersForSwitch(int whichSwitch) {
//		int startServer = 0;
//		for (int sw=0; sw<noNodes; sw++) {
//			if (sw < whichSwitch) {
//				startServer += weightEachNode[sw];
//			}
//		}
//
//		int numServers = weightEachNode[whichSwitch];
//		int[] serverarr = new int[numServers];
//		for (int s=0; s<numServers; s++) {
//			serverarr[s] = s+startServer;
//		}
//		return serverarr;
//	}
}
