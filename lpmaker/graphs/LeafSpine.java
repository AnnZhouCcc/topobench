package lpmaker.graphs;

public class LeafSpine extends Graph {

	int numLeafSwitches;
	int numSpineSwitches;
	int numServers;

	public LeafSpine(int _numSwitches, int _numPorts, int _numSpineSwitches, int _numServers){
		super(_numSwitches);
		numPorts = _numPorts;
		noNodes = _numSwitches;
		numLeafSwitches = _numSwitches - _numSpineSwitches;
		numSpineSwitches = _numSpineSwitches;
		numServers = _numServers;
		name="leafspine";
		checkLeafSpineValidity();
		populateAdjacencyList();
	}

	private void checkLeafSpineValidity() {
		if (numServers != numLeafSwitches*(numPorts-numSpineSwitches)) {
			System.out.println("**Warning: the leafspine graph setup is not valid.");
			System.exit(0);
		}
	}

	private void printAdjacencyList() {
		for (int i=0; i<adjacencyList.length; i++) {
			System.out.print(i + ": ");
			for (int j=0; j<adjacencyList[i].size(); j++) {
				System.out.print(adjacencyList[i].get(j).linkTo + " ");
			}
			System.out.println();
		}
	}

	private void populateAdjacencyList(){
		for (int spinesw=0; spinesw<numSpineSwitches; spinesw++) {
			for (int leafsw=numSpineSwitches; leafsw<noNodes; leafsw++) {
				if (!isNeighbor(spinesw,leafsw)) addBidirNeighbor(spinesw,leafsw);
			}
		}

		setUpFixWeight(0);
		for (int spinesw=0; spinesw<numSpineSwitches; spinesw++) {
			weightEachNode[spinesw] = 0;
		}
		for (int leafsw=numSpineSwitches; leafsw<noNodes; leafsw++) {
			weightEachNode[leafsw] = numPorts - numSpineSwitches;
		}

		totalWeight = numServers;
		System.out.println("total number of servers = " + totalWeight);
	}

	public int svrToSwitch(int whichServer) {
		int currentServers = 0;
		for (int sw=0; sw<noNodes; sw++) {
			int numServersThisSw = weightEachNode[sw];
			if (currentServers + numServersThisSw > whichServer) {
				return sw;
			} else {
				currentServers += numServersThisSw;
			}
		}
		System.out.println("Failed to locate the server on any switch.");
		System.out.println("server id = " + whichServer + ", number of switches = " + noNodes + ", number of ports = " + numPorts);
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
}
