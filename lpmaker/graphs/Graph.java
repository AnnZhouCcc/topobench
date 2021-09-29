/* *******************************************************
 * Released under the MIT License (MIT) --- see ../../LICENSE
 * Copyright (c) 2014 Ankit Singla, Sangeetha Abdu Jyothi, Chi-Yao Hong, Lucian Popa, P. Brighten Godfrey, Alexandra Kolla
 * ******************************************************** */

package lpmaker.graphs;

import java.util.*;
import java.io.*;

import lpmaker.*;

class FlowSrcDst {
	int src;
	int dst;

	FlowSrcDst (int _src, int _dst) {
		src = _src;
		dst = _dst;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FlowSrcDst that = (FlowSrcDst) o;
		return src == that.src && dst == that.dst;
	}

	@Override
	public int hashCode() {
		return Objects.hash(src, dst);
	}
}

public class Graph
{
	public static final int INFINITY = 999999999;
	public static final int INVALID_NODE_ID = -1;

	public int noNodes;
	public int numEdges;
	public Vector<Link>[] adjacencyList;

	public int[][] shortestPathLen;

	//no of sub-hosts of a switch
	public int[] weightEachNode;
	public int[] weightBeforeEachNode;
	public int totalWeight = 0;

	//a common seed used for both PrintToMCF and PrintToMCFFair
	public long gseed;

	//no of packets at this node's Q
	public int[] localQ;

	//creation buffer
	public int[] creationBuffer;

	//default number of hosts associated to each node in the graph
	public static final int DEFAULT_NO_HOSTS = 1;

//	int nCommodities; <- not really used

	protected boolean[] isSwitch;

	public static Random rand;
	public String name = "graph";

	public Graph()
	{
		rand = ProduceLP.universalRand;
	}

	public Graph(int size)
	{
		rand = ProduceLP.universalRand;
		noNodes = size;
		localQ = new int[noNodes];
		creationBuffer = new int[noNodes];
		allocateAdjacencyList();
		setUpFixWeight(DEFAULT_NO_HOSTS);
		setUpAllSwitches();
	}

	public Graph(int size, int noHosts)
	{
		noNodes = size;
		rand = ProduceLP.universalRand;
		localQ = new int[noNodes];
		creationBuffer = new int[noNodes];
		allocateAdjacencyList();
		setUpFixWeight(noHosts);
		setUpAllSwitches();
	}

	public void randomConstructorPerms(Vector<Integer> in_set, Vector<Integer> in_degrees, int cap)
	{
		// Clone the vectors
		Vector<Integer> still_to_link = new Vector<Integer>();
		Vector<Integer> degrees = new Vector<Integer>();
		for (int i = 0; i < in_set.size(); i++) still_to_link.add(((Integer)in_set.elementAt(i)));
		for (int i = 0; i < in_degrees.size(); i++) degrees.add(((Integer)in_degrees.elementAt(i)));
		int[] linksAdded = new int[noNodes];

		boolean found = true;

		while(found)
		{
			//< Permute still_to_link
			int[] permArr = new int[still_to_link.size()];
			for (int i = 0; i < permArr.length; i++)
				permArr[i] = still_to_link.elementAt(i).intValue();

			for(int i = permArr.length; i > 1; i--)
			{
				// swap permArr[i-1] and permArr[rand(0 to i-1)]
				int temprand = rand.nextInt(i);
				int tempswap = permArr[temprand];
				permArr[temprand] = permArr[i-1];
				permArr[i-1] = tempswap;
			}
			//>

			//< Connect stuff not already connected, and clean up
			found = false;
			for (int i = 0; i < permArr.length; i++)
			{
				if (isNeighbor(still_to_link.elementAt(i).intValue(), permArr[i])) continue;

				found = true;
				addBidirNeighbor(permArr[i], still_to_link.elementAt(i).intValue(), cap);
				linksAdded[still_to_link.elementAt(i).intValue()]++;
				linksAdded[permArr[i]]++;
			}

			still_to_link.clear();
			degrees.clear();
			for (int i = 0; i < in_set.size(); i++)
			{
				int remDeg = in_degrees.elementAt(i) - linksAdded[in_set.elementAt(i)];
				if (remDeg > 1)
				{
					degrees.add(Integer.valueOf(remDeg));
					still_to_link.add(Integer.valueOf(in_set.elementAt(i)));
				}
			}
			if (still_to_link.size() < 2) break;
			//>
		}

		// Edge swaps to fix things up
		System.out.println("Construction phase 2");
		still_to_link.clear();
		degrees.clear();
		for (int i = 0; i < in_set.size(); i++)
		{
			int remDeg = in_degrees.elementAt(i) - linksAdded[in_set.elementAt(i)];
			if (remDeg > 0)
			{
				degrees.add(Integer.valueOf(remDeg));
				still_to_link.add(Integer.valueOf(in_set.elementAt(i)));
			}
		}

		int fix_iter = 0;
		while (fix_iter < 5000 && still_to_link.size() != 0)
		{
			fix_iter ++;
			int badNode = still_to_link.elementAt(0);
			int degFix = degrees.elementAt(0);
			int anotherBad = badNode;

			if (degFix == 1)   // Find another different bad node
			{
				if (still_to_link.size() == 1) return;
				anotherBad = still_to_link.elementAt(1);
			}

			// Locate edge to break
			int randNode1 = badNode;
			int randNode2 = badNode;
			while (randNode1 == badNode || randNode1 == anotherBad || randNode2 == badNode ||
			        randNode2 == anotherBad || isNeighbor(badNode, randNode1) || isNeighbor(anotherBad, randNode2))
			{
				randNode1 = ((Integer)in_set.elementAt(rand.nextInt(in_set.size()))).intValue();
				int mycap = 0;
				do {
					int rndnbr = rand.nextInt(adjacencyList[randNode1].size());
					randNode2 = adjacencyList[randNode1].elementAt(rndnbr).intValue();
					mycap = adjacencyList[randNode1].elementAt(rndnbr).linkcapacity;

				}
				while (!in_set.contains(Integer.valueOf(randNode2)) || mycap != cap);
			}

			// Swap
			removeBidirNeighbor(randNode1, randNode2);
			addBidirNeighbor(badNode, randNode1, cap);
			addBidirNeighbor(anotherBad, randNode2, cap);
			fix_iter = 0;

			// Fix still_to_link and degrees
			if (degFix == 1)
			{
				degrees.set(0, degFix - 1);
				degrees.set(1, degrees.elementAt(1) - 1);
			}
			else degrees.set(0, degFix - 2);

			if (degrees.elementAt(0) == 0)
			{
				still_to_link.remove(0);
				degrees.remove(0);
			}
			if (still_to_link.size() == 0) break;
			if (degrees.elementAt(0) == 0)
			{
				still_to_link.remove(0);
				degrees.remove(0);
			}

			if (still_to_link.size() < 2) continue;
			if (degrees.elementAt(1) == 0)
			{
				still_to_link.remove(1);
				degrees.remove(1);
			}
		}
		System.out.println("Construction done!");
	}

	public void randomConstructor(Vector<Integer> in_set, Vector<Integer> in_degrees, int cap)
	{
		// Clone the vectors
		Vector<Integer> still_to_link = new Vector<Integer>();
		Vector<Integer> degrees = new Vector<Integer>();
		int[] linksAdded = new int[noNodes];

		for (int i = 0; i < in_set.size(); i++) still_to_link.add(((Integer)in_set.elementAt(i)));
		for (int i = 0; i < in_degrees.size(); i++) degrees.add(((Integer)in_degrees.elementAt(i)));

		int stop_sign = 0;

		while(!still_to_link.isEmpty() && stop_sign==0)
		{
			if(still_to_link.size() == 1) 				// Ignores this case of 1 node left out
			{
				stop_sign=1;
			}
			boolean found = false;

			int p1 = -1, p2 = -1;
			Integer n1 = Integer.valueOf(0);
			Integer n2 = Integer.valueOf(0);

			int iteration = 0;
			int MAX_ITERATION = 1000;

			while(!found && iteration++ < MAX_ITERATION && stop_sign == 0)  // try until a node-pair to connect is found
			{
				p1 = rand.nextInt(still_to_link.size());
				p2 = p1;
				while(p2 == p1)
				{
					p2 = rand.nextInt(still_to_link.size());
				}

				n1 = (Integer)still_to_link.elementAt(p1);
				n2 = (Integer)still_to_link.elementAt(p2);

				// Check if an n1-n2 edge already exists
				int k=0;
				for(int i=0; i<adjacencyList[n1.intValue()].size(); i++)
					if(adjacencyList[n1.intValue()].elementAt(i).intValue() == n2) k=1;

				if(k==0)   // Edge doesn't already exist. Good, add it!
				{
					found = true;
					addBidirNeighbor(n1, n2, cap);
					linksAdded[n1]++;
					linksAdded[n2]++;
				}
			}

			if(stop_sign==0)
			{
				/*
				 * If a clique of nodes is left in the end, this gives up
				 */

				if(iteration >= MAX_ITERATION) 	// Give up if can't find a pair to link
				{
					System.out.println("WARNING: Unable to find new pair for link between:"+still_to_link);
					stop_sign=1;
				}
				degrees.set(p1, Integer.valueOf(((Integer)(degrees.elementAt(p1))).intValue()-1));
				degrees.set(p2, Integer.valueOf(((Integer)(degrees.elementAt(p2))).intValue()-1));
				boolean deleted_p1 = false;

				if(((Integer)degrees.elementAt(p1)).intValue() == 0)
				{
					// Degree exhausted => No longer available to connect
					still_to_link.remove(p1);
					degrees.remove(p1);
					deleted_p1 = true;
				}

				// Just adjusting the vector index here, nothing related to high-level function
				int p2_updated;
				if(deleted_p1 && p1 < p2)
					p2_updated = p2-1;
				else
					p2_updated = p2;

				if(((Integer)degrees.elementAt(p2_updated)).intValue() == 0)
				{
					// Degree exhausted => No longer available to connect
					still_to_link.remove(p2_updated);
					degrees.remove(p2_updated);
				}
			}
		}

		// Edge swaps to fix things up
		System.out.println("Construction phase 2");
		still_to_link.clear();
		degrees.clear();
		for (int i = 0; i < in_set.size(); i++)
		{
			int remDeg = in_degrees.elementAt(i) - linksAdded[in_set.elementAt(i)];
			if (remDeg > 0)
			{
				degrees.add(Integer.valueOf(remDeg));
				still_to_link.add(Integer.valueOf(in_set.elementAt(i)));
			}
		}

		int fix_iter = 0;
		while (fix_iter < 5000 && still_to_link.size() != 0)
		{
			fix_iter ++;
			int badNode = still_to_link.elementAt(0);
			int degFix = degrees.elementAt(0);
			int anotherBad = badNode;

			if (degFix == 1)   // Find another different bad node
			{
				if (still_to_link.size() == 1) return;
				anotherBad = still_to_link.elementAt(1);
			}

			// Locate edge to break
			int randNode1 = badNode;
			int randNode2 = badNode;
			while (randNode1 == badNode || randNode1 == anotherBad || randNode2 == badNode ||
			        randNode2 == anotherBad || isNeighbor(badNode, randNode1) || isNeighbor(anotherBad, randNode2))
			{
				randNode1 = ((Integer)in_set.elementAt(rand.nextInt(in_set.size()))).intValue();
				int mycap = 0;
				do {
					int rndnbr = rand.nextInt(adjacencyList[randNode1].size());
					randNode2 = adjacencyList[randNode1].elementAt(rndnbr).intValue();
					mycap = adjacencyList[randNode1].elementAt(rndnbr).linkcapacity;

				}
				while (!in_set.contains(Integer.valueOf(randNode2)) || mycap != cap);
			}

			// Swap
			removeBidirNeighbor(randNode1, randNode2);
			addBidirNeighbor(badNode, randNode1, cap);
			addBidirNeighbor(anotherBad, randNode2, cap);
			fix_iter = 0;

			// Fix still_to_link and degrees
			if (degFix == 1)
			{
				degrees.set(0, degFix - 1);
				degrees.set(1, degrees.elementAt(1) - 1);
			}
			else degrees.set(0, degFix - 2);

			if (degrees.elementAt(0) == 0)
			{
				still_to_link.remove(0);
				degrees.remove(0);
			}
			if (still_to_link.size() == 0) break;
			if (degrees.elementAt(0) == 0)
			{
				still_to_link.remove(0);
				degrees.remove(0);
			}

			if (still_to_link.size() < 2) continue;
			if (degrees.elementAt(1) == 0)
			{
				still_to_link.remove(1);
				degrees.remove(1);
			}
		}
	}

	protected void allocateAdjacencyList()
	{
		if(noNodes == 0)
			throw new RuntimeException("Attempting to allocate the adjacency list before setting the number of nodes");
		adjacencyList = new Vector[noNodes];
		for(int i = 0; i < noNodes; i++)
		{
			adjacencyList[i] = new Vector();
		}
	}

	protected void setUpFixWeight(int weight)
	{
		weightEachNode = new int[noNodes];
		weightBeforeEachNode = new int[noNodes+1];
		for(int i = 0; i < noNodes; i++)
		{
			weightEachNode[i] = weight;
			weightBeforeEachNode[i] = i * weight;
		}
	}

	protected void setUpAllSwitches()
	{
		isSwitch = new boolean[noNodes];
		for(int i = 0; i < noNodes; i++)
		{
			isSwitch[i] = true;
			localQ[i] = 0;
		}
	}

	public int getNoNodes()
	{
		return noNodes;
	}

	//should be overloaded
	public int getNoHosts()
	{
		throw new RuntimeException("getNoHosts should be overloaded in derived classes");
	}

	//should be overloaded
	public int svrToSwitch(int index)
	{
		throw new RuntimeException("svrToSwitch should be overloaded in derived classes");
	}

	//should be overloaded
	public int getNoSwitches()
	{
		throw new RuntimeException("getNoSwitches should be overloaded in derived classes");
	}


	public Vector<Link>[] getAdjacencyList()
	{
		return adjacencyList;
	}


	protected boolean addBidirNeighbor(int n1, int n2)
	{
		return addBidirNeighbor(Integer.valueOf(n1), Integer.valueOf(n2));
	}

	protected boolean removeBidirNeighbor(int n1, int n2, int cap)
	{
		boolean deleted = false;
		for(int i=0; i< adjacencyList[n1].size(); i++)
			if(adjacencyList[n1].elementAt(i).intValue() == n2)
			{
				if (adjacencyList[n1].elementAt(i).linkcapacity > cap)
					adjacencyList[n1].elementAt(i).linkcapacity -= cap;
				else adjacencyList[n1].remove(i);
				deleted = true;
			}
		for(int i=0; i< adjacencyList[n2].size(); i++)
		{
			if(adjacencyList[n2].elementAt(i).intValue() == n1)
			{
				if (adjacencyList[n2].elementAt(i).linkcapacity > cap)
					adjacencyList[n2].elementAt(i).linkcapacity -= cap;
				else adjacencyList[n2].remove(i);
			}
		}
		return deleted;
	}

	protected boolean removeBidirNeighbor(int n1, int n2)
	{
		boolean deleted = false;
		for(int i=0; i< adjacencyList[n1].size(); i++)
			if(adjacencyList[n1].elementAt(i).intValue() == n2)
			{
				adjacencyList[n1].remove(i);
				deleted = true;
			}
		for(int i=0; i< adjacencyList[n2].size(); i++)
			if(adjacencyList[n2].elementAt(i).intValue() == n1)
				adjacencyList[n2].remove(i);
		return deleted;
	}

	protected boolean addBidirNeighbor(Integer n1, Integer n2)
	{
		if(!addNeighbor(n1.intValue(),n2))
			return false;
		if(!addNeighbor(n2.intValue(),n1))
			return false;
		return true;
	}

	protected boolean addBidirNeighbor(int n1, int n2, int cap)
	{
		return addBidirNeighbor(Integer.valueOf(n1), Integer.valueOf(n2), cap);
	}

	protected boolean addBidirNeighbor(Integer n1, Integer n2, int cap)
	{
		if(!addNeighbor(n1.intValue(),n2, cap))
			return false;
		if(!addNeighbor(n2.intValue(),n1, cap))
			return false;
		return true;
	}

	protected boolean addNeighbor(int n1, int n2)
	{
		return addNeighbor(n1, Integer.valueOf(n2));
	}

	protected boolean addNeighbor(int n1, int n2, int cap)
	{
		return addNeighbor(n1, Integer.valueOf(n2), cap);
	}

	protected boolean addNeighbor(int n1, Integer n2_i)
	{
		if(n1 == n2_i.intValue())
		{
			System.err.println("Warning: Graph: addNeighbor: trying to add link to itself for "+n1+" ignoring...");
			return false;
		}
		if(n2_i.intValue() >= noNodes)
		{
			throw new RuntimeException("ERROR: Graph: addNeighbor: adding link to non existing node, between node"+n1+" and "+n2_i.intValue());
		}

		// We allow parallel links! We increase the capacity of link by 1 unit.
		for(int i=0; i<adjacencyList[n1].size(); i++)
		{
			if(adjacencyList[n1].get(i).intValue() == n2_i.intValue())
			{
				adjacencyList[n1].elementAt(i).linkcapacity += 1;
				return true;
			}
		}
		adjacencyList[n1].add(new Link(n2_i));
		return true;
	}

	protected boolean addNeighbor(int n1, Integer n2_i, int cap)
	{
		if(n1 == n2_i.intValue())
		{
			return false;
		}
		if(n2_i.intValue() >= noNodes)
		{
			throw new RuntimeException("ERROR: Graph: addNeighbor: adding link to non existing node, between node"+n1+" and "+n2_i.intValue());
		}

		// We allow parallel links! We increase the capacity of link by 1 unit.
		for(int i=0; i<adjacencyList[n1].size(); i++)
		{
			if(adjacencyList[n1].get(i).intValue() == n2_i.intValue())
			{
				adjacencyList[n1].elementAt(i).linkcapacity += cap;
				return true;
			}
		}
		adjacencyList[n1].add(new Link(n2_i, cap));
		return true;
	}
	public void assertNeighbor(int n1, int n2)
	{
		if(!adjacencyList[n1].contains(Integer.valueOf(n2)))
		{
			throw new RuntimeException("ERROR: neighbor assertion failed: "+n2+" is not a neighbor of current "+n1);
		}
	}

	public boolean isNeighbor(int n1, int n2)
	{
		if (n1 == n2) return true;
		for (int i = 0; i < adjacencyList[n1].size(); i++)
			if (adjacencyList[n1].elementAt(i).intValue() == n2) return true;
		return false;
	}
	public Link findNeighbor(int n1, int n2)
	{
		for (int i = 0; i < adjacencyList[n1].size(); i++)
		{
			if (adjacencyList[n1].elementAt(i).intValue() == n2)
				return adjacencyList[n1].elementAt(i);
		}
		return null;
	}

	public void assertBidirectional()
	{
		for(int i = 0; i < noNodes; i++)
		{
			Integer i_obj = Integer.valueOf(i);
			for(int j = 0; j < adjacencyList[i].size(); j++)
			{
				if(findNeighbor(adjacencyList[i].get(j).intValue(), i) == null)
				{
					throw new RuntimeException("Graph NOT Bidirectional: node "+i+" has neighbor "+adjacencyList[i].get(j).intValue()+" but the reciprocal is not true");
				}
			}
		}
	}

	public void assertConstantDegree(int expected_degree)
	{
		for(int i = 0; i < noNodes; i++)
		{
			if(adjacencyList[i].size() != expected_degree)
			{
				throw new RuntimeException("Graph: Constant Degree Assertion failed: number of links of node "+i+" is "+adjacencyList[i].size()+" expecting "+expected_degree);
			}
		}
	}

	public int[] getWeightEachNode()
	{
		return weightEachNode;
	}

    	public int[] getWeightBeforeEachNode()
    	{
        	return weightBeforeEachNode;
    	}

	public boolean isSwitch(int node_id)
	{
		return isSwitch[node_id];
	}

	public String toString()
	{
		String s = "";
		for(int i = 0; i < noNodes; i++)
		{
			s += i + " " + weightEachNode[i];
			for(int j = 0; j < adjacencyList[i].size(); j++)
			{
				s += " " + adjacencyList[i].elementAt(j).intValue() + " (" + adjacencyList[i].elementAt(j).linkcapacity + ")";
			}
			if (i != noNodes - 1) s += "\n";
		}
		return s;
	}


	protected void createBidirLink(int i, int j)
	{
		adjacencyList[i].add(new Link(j));
		adjacencyList[j].add(new Link(i));
	}

	public void failLinks(double percentage)
	{
		// First find the total number of edges
		int totalEdges = 0;
		for (int i =0; i < noNodes; i++)
		{
			totalEdges += adjacencyList[i].size();
		}
		totalEdges /= 2;

		int numFail = (int) (percentage * totalEdges);
		System.out.println("TO-FAIL LINKS NUM = " +  numFail);

		int failed_until_now = 0;

		Vector<Integer> failedLinks = new Vector<Integer>();

		while (failed_until_now < numFail)
		{
			totalEdges = 0;
			for (int i =0; i < noNodes; i++)
			{
				totalEdges += adjacencyList[i].size();
			}
			totalEdges /= 2;
			int linkToFail = rand.nextInt(totalEdges);

			//if (!failedLinks.contains((Integer) linkToFail)) {
			failedLinks.clear();
			failedLinks.add((Integer) linkToFail);
			failed_until_now ++;

			//Fail the link linkToFail
			LinkedList<Link> ll = new LinkedList<Link>();

			// Now we've decided which links have failed. Unplug them!
			int index_read = 0;
			int counter = 0;
			for (int i = 0; i < noNodes; i++)
			{
				Iterator<Link> my_iter = adjacencyList[i].iterator();
				while(my_iter.hasNext())
				{
					Link curr = my_iter.next();
					if (failedLinks.contains((Integer) index_read))
					{
						counter++ ;
						//my_iter.remove();
						ll.add(curr);
						for (Link reverseLink : adjacencyList[curr.linkTo])
							if (reverseLink.linkTo == i)
							{
								//adjacencyList[curr.linkTo].remove(reverseLink);
								counter++;
								ll.add(reverseLink);
								break;
							}
					}
					index_read ++;
				}
			}
			int total_removed_link=0;
			for(int i=0; i<noNodes; i++)
			{
				int k = adjacencyList[i].size();
				adjacencyList[i].removeAll(ll);
				total_removed_link += k - adjacencyList[i].size();
			}

			System.out.println("CYH: We remove the number of cables this time:" + total_removed_link);
		}
	}

	public int getShortestLength(int srcTor, int destTor)
	{
		return shortestPathLen[srcTor][destTor];
	}

	/*remembers all shortest paths*/
	public void modifiedFloydWarshall()
	{
		shortestPathLen = new int[noNodes][noNodes];

		for(int i = 0; i < noNodes; i++)
		{
			for(int j = 0; j < noNodes; j++)
			{
				if(i == j)
					shortestPathLen[i][j] = 0;
				else if(findNeighbor(i,j) != null)
				{
					shortestPathLen[i][j] = 1;//adjacencyMatrix[i][j];
				}
				else
				{
					shortestPathLen[i][j] = Graph.INFINITY;
				}
			}
		}

		//floyd warshall
		for(int k = 0; k < noNodes; k++)
		{
			for(int i = 0; i < noNodes; i++)
			{
				for(int j = 0; j < noNodes; j++)
				{
					if(shortestPathLen[i][j] > shortestPathLen[i][k] + shortestPathLen[k][j])
					{
						shortestPathLen[i][j] = shortestPathLen[i][k] + shortestPathLen[k][j];
					}
				}
			}
		}
	}

	// Print shortestpaths
	public void printPathLengths(String plFile)
	{
		try
		{
			modifiedFloydWarshall();
			FileWriter fstream = new FileWriter(plFile);
			BufferedWriter out = new BufferedWriter(fstream);

			for (int i = 0 ; i < noNodes; i++)
			{
				for (int j = 0 ; j < noNodes; j++)
				{
					if (weightEachNode[i] > 0 && weightEachNode[j] > 0 && i != j)
					{
						int printWeight = weightEachNode[i] * weightEachNode[j];
						out.write(shortestPathLen[i][j] + " " + printWeight + "\n");
					}
				}
			}
			out.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private class FlowID
	{
		public int flowID;
		public int srcSwitch;
		public int dstSwitch;
		public FlowID(int fid, int s, int d)
		{
			flowID = fid;
			srcSwitch = s;
			dstSwitch = d;
		}
	}

	// If slack is set to 3, flows can not deviate more than 2 hops from their shortest path distance
	private boolean isFlowZero(FlowID flowID, int linkFrom, int linkTo)
	{
		int SLACK = 6;
		int srcSw = flowID.srcSwitch;
		int destSw = flowID.dstSwitch;

		if((shortestPathLen[srcSw][linkFrom] + 1 + shortestPathLen[linkTo][destSw]) >= (shortestPathLen[srcSw][destSw] + SLACK))
			return true;
		else
			return false;
	}

	// Uses a path-length based heuristic to determine that a certain flow on a certain link will be 0, so no need to factor in LP etc.
	public void PrintGraphforMCFFairCondensed(String filename, int probability, double[][] switchLevelMatrix, ArrayList<Rack> rackPool, ArrayList<Path>[][] pathPool)
	{

		modifiedFloydWarshall();
		int r=noNodes; //# of ToR switches
		int svrs=totalWeight;
		boolean useOptimalRouting = true;
		if (rackPool != null) {
			assert(pathPool != null);
			useOptimalRouting = false;
		}

		int nflowlet = 1;
		try
		{
			FileWriter fstream = new FileWriter(filename);
			BufferedWriter out = new BufferedWriter(fstream);

			// Nodes
			int numEdges = 0;
			for(int i=0; i<noNodes; i++)
			{
				numEdges += adjacencyList[i].size();
			}

			// Edges
			int edgeID = 0;
			int edgeCapacity = 1;
			for(int i=0; i<noNodes; i++)
			{
				for(int j=0; j<adjacencyList[i].size(); j++)
				{
					int to = adjacencyList[i].elementAt(j).intValue();
					edgeID++;
				}
			}

			// Commodities
			int commodityIndex = 0;
			for (int f = 0; f < noNodes; f ++)
				for (int t = 0; t < noNodes; t++)
					if (switchLevelMatrix[f][t] != 0) commodityIndex ++;


			int numFlows = 0;
			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
						numFlows++;

			String file_index = filename.substring(3); 
			file_index = file_index.substring(0, file_index.length() - 4);

			System.out.println(file_index + " ***************************** ");

			FlowID[] allFlowIDs = new FlowID[numFlows];
			int curfID=0;
			Writer output1 = new BufferedWriter(new FileWriter("flowIDmap" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
					{
						allFlowIDs[curfID] = new FlowID(curfID, f, t);
						output1.write(curfID + " " + f + " " + t + "\n");
						curfID++;
					}
			output1.close();
//
//			Writer output2 = new BufferedWriter(new FileWriter("linkCaps" + file_index));
//
//			for (int f = 0; f < noNodes; f++)
//				for (int j=0; j<adjacencyList[f].size(); j++) {  //for each out link of f = (f,j)
//					String lType = "";
//
//					if (adjacencyList[f].elementAt(j).linkcapacity > 1) lType = "H-H";
//					else lType += adjacencyList[f].size() + "-" + adjacencyList[adjacencyList[f].elementAt(j).intValue()].size();
//
//					output2.write(f + "_" + adjacencyList[f].elementAt(j).intValue() + " " + adjacencyList[f].elementAt(j).linkcapacity + " " + adjacencyList[f].size() + " " + lType + "\n");
//				}
//			output2.close();

			//boolean fair = false;
			boolean fair = true;
			int fid=0;
			String constraint = "";
			if (fair) {
                // we need to set probability% of flows to large flows.
                // Rand(100) < probability does not give desired distribution for small network sizes
                // Hence, we shuffle the flows and choose the first round(prob% * totalFlows) number of flows
                ArrayList<Integer> list = new ArrayList<Integer>();
                for (int i = 0; i < curfID; i++) {
                    list.add(Integer.valueOf(i));
                }
                Collections.shuffle(list);
                float prob = (float) probability / 100;
                int chooseCount = Math.round(prob * curfID);

				//< Objective
				out.write("Maximize \n");
				out.write("obj: ");
				String objective = "K";

				// To make CPLEX not fill pipes as freely as it does while keeping the optimal value same
				// Simple idea: For each utilization of capacity, subtract a tiny amount from the objective. 
				// This forces CPLEX to keep the main 'K' part as large as possible, while avoiding wastage of capacity
				/*for (int f = 0; f < noNodes; f++)
				  for (int j=0; j<adjacencyList[f].size(); j++) {  //for each out link of f = (f,j)
				  for (int fid = 0; fid < numFlows; fid ++) {
				  if (!isFlowZero(allFlowIDs[fid], f, adjacencyList[f].elementAt(j).intValue())) {
				  double normalized_factor = 0.00000001 / adjacencyList[f].elementAt(j).linkcapacity;
				  objective += " -" + (new BigDecimal(Double.toString(normalized_factor))).toPlainString() + "f_" + fid + "_" + f + "_" + adjacencyList[f].elementAt(j).intValue(); 
				  }
				  }
				  }*/

				out.write(objective);


				//<Constraints of Type 0: fairness i.e. flow >= K
				out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
				System.out.println(new Date() + ": Starting part 0");
				for (int f = 0; f < noNodes; f++)
				{
					for (int t = 0; t < noNodes; t++)
					{
						if(switchLevelMatrix[f][t]>0)	  //for each flow fid with source f
						{
							constraint = "c0_" + fid + ": ";
							//System.out.println("CHECK FLOW =========== " + fid + " " + f + " " + t);

							int writeCons = 0;
							for(int j=0; j<adjacencyList[f].size(); j++)   //for each out link of f = (f,j)
							{
								if (!isFlowZero(allFlowIDs[fid], f, adjacencyList[f].elementAt(j).intValue()))
								{
									constraint += "-f_" + fid + "_" + f + "_" + adjacencyList[f].elementAt(j).intValue() + " ";
									writeCons = 1;
								}
								//if(j!=adjacencyList[f].size()-1) constraint += "- ";
							}
							if (writeCons == 1)
							{
                                Random rand = new Random();
                                if (list.indexOf(fid) < chooseCount && probability < 100) {
                                    constraint += " + " + 10*switchLevelMatrix[f][t] + " K <= 0\n";
                                } else {
                                    constraint += " + " + switchLevelMatrix[f][t] + " K <= 0\n";
                                }
                                out.write(constraint);
							}
							fid++;
						}
					}
				}
				//>
			}

			//>
			else { // no fairness constraints -- max total throughput
				//< Objective
	                        out.write("Maximize \n");
	                        out.write("obj: ");
				fid = 0;
	                        String objective = "";
	                        for (int f = 0; f < noNodes; f++) {
	                                for (int t = 0; t < noNodes; t++) {
	                                        if(switchLevelMatrix[f][t]>0)   { //for each flow fid with source f
	                                                for(int j=0; j<adjacencyList[f].size(); j++) { //for each out link of f = (f,j)
	                                                        objective += "f_" + fid + "_" + f + "_" + adjacencyList[f].elementAt(j).intValue() + " ";
	                                                        if(j!=adjacencyList[f].size()-1)
	                                                                objective += "+ ";
	                                                }
	                                                if(fid != commodityIndex-1)
	                                                        objective += "+ ";
	                                                else
	                                                        objective += "\n";
	                                                fid++;
	                                        }
	                                }
	                        }
	                        out.write(objective);
				out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
	                        //>
			}

			//<Constraints of Type 1: Load on link <= max_load
			out.write("\n\\Type 1: Load on link <= max_load\n");
			System.out.println(new Date() + ": Starting part 1");
			constraint = "";
			int strCapacity = 25*commodityIndex;
			for(int i=0; i<noNodes; i++)
			{
				for(int j=0; j<adjacencyList[i].size(); j++)
				{
					StringBuilder curConstraint = new StringBuilder(strCapacity);
					int writeCons = 0;
					for(int fd_=0; fd_<commodityIndex; fd_++)
					{
						//for each flow fd_
						if (!isFlowZero(allFlowIDs[fd_], i, adjacencyList[i].elementAt(j).intValue()))
						{
							//constraint += "f_" + fd_ + "_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + " + ";
							curConstraint.append("f_" + fd_ + "_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + " + ");
							writeCons = 1;
						}
					}
					constraint = curConstraint.toString();
					if(constraint.endsWith("+ "))
						constraint = constraint.substring(0, constraint.lastIndexOf("+")-1);
					//System.out.println("string size: "+constraint.length());
					if(writeCons == 1)
					{
						out.write("c1_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + ": " + constraint + " <= " +  adjacencyList[i].elementAt(j).linkcapacity + "\n");
						//constraint = "";
					}
				}
				if(i > 0 && i % 20 == 0)
					System.out.println(new Date() + ": "+i+" of "+noNodes+" done");
			}
			//>

			//<Constraints of Type 2: Flow conservation at non-source, non-destination
			int LARGE_VALUE = 1;		// TOPO_COMPARISON
			System.out.println(new Date() + ": Starting part 2");
			out.write("\n\\Type 2: Flow conservation at non-source, non-destination\n");

			if (useOptimalRouting) {

				fid = 0;
				for (int f = 0; f < noNodes; f++) {
					for (int t = 0; t < noNodes; t++) {
						if (switchLevelMatrix[f][t] > 0)       //for each flow fid
						{
							for (int u = 0; u < noNodes; u++)   //for each node u
							{
								constraint = "";
								int writeCons = 0;
								if (u == f)    //src
								{
									constraint = "c2_" + fid + "_" + u + "_1: ";

									for (int j = 0; j < adjacencyList[u].size(); j++)   //for each out link of u = (u,j)
									{
										if (!isFlowZero(allFlowIDs[fid], u, adjacencyList[u].elementAt(j).intValue())) {
											constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
											writeCons = 1;
										}
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1)
										out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");
									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_2: ";
									for (int j = 0; j < adjacencyList[u].size(); j++)   //for each in link of u = (j,u)
									{
										if (!isFlowZero(allFlowIDs[fid], adjacencyList[u].elementAt(j).intValue(), u)) {
											constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " + ";
											writeCons = 1;
										}
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1) out.write(constraint + " = 0\n");
								} else if (u == t) {
								} else  // non-src and non-dest
								{
									constraint = "c2_" + fid + "_" + u + "_3: ";
									for (int j = 0; j < adjacencyList[u].size(); j++)   //for each out link of u = (u,j)
									{
										if (!isFlowZero(allFlowIDs[fid], u, adjacencyList[u].elementAt(j).intValue())) {
											constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
											writeCons = 1;
										}
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									constraint += " - ";

									for (int j = 0; j < adjacencyList[u].size(); j++)   //for each in link of u = (j,u)
									{
										if (!isFlowZero(allFlowIDs[fid], adjacencyList[u].elementAt(j).intValue(), u)) {
											constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " - ";
											writeCons = 1;
										}
									}
									if (constraint.endsWith("- "))
										constraint = constraint.substring(0, constraint.lastIndexOf("-") - 1);
									if (writeCons == 1) out.write(constraint + " = 0\n");
								}
							}
							fid++;
						}
					}
					if (f > 0 && f % 20 == 0)
						System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
				}
			} else {

				fid = 0;
				for (int f = 0; f < noNodes; f++) {
					for (int t = 0; t < noNodes; t++) {
						if (switchLevelMatrix[f][t] > 0)       //for each flow fid from f to t
						{
							for (int u = 0; u < noNodes; u++)   //for each node u
							{
								constraint = "";
								int writeCons = 0;
								if (u == f)    //src
								{

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_1: ";
									HashSet<Integer> feasibleOutgoingLinkDst = rackPool.get(u).outgoingLinkDst[f][t];
									for (Integer linkDst : feasibleOutgoingLinkDst) { // for feasible out link of u = (u,j)
										constraint += "f_" + fid + "_" + u + "_" + linkDst.intValue() + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1)
										out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_2: ";
									for (int j = 0; j < adjacencyList[u].size(); j++) // for every other out link of u = (u,j)
									{
										if (feasibleOutgoingLinkDst.contains(adjacencyList[u].elementAt(j).intValue())) continue;
										constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1)
										out.write(constraint + " = 0\n");

									// A flow starting from u should not come back to u for any reason.
									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_2: ";
									for (int j = 0; j < adjacencyList[u].size(); j++)   //for each in link of u = (j,u)
									{
										constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1) out.write(constraint + " = 0\n");

								} else if (u == t) { // dst

									// A flow ending at u should not go out from u again for any reason.
									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_3: ";
									for (int j = 0; j < adjacencyList[u].size(); j++) // for each out link of u = (u,j)
									{
										constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1)
										out.write(constraint + " = 0\n");

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_4: ";
									HashSet<Integer> feasibleIncomingLinkSrc = rackPool.get(u).incomingLinkSrc[f][t];
									for (Integer linkSrc : feasibleIncomingLinkSrc) { // for feasible in link of u = (j,u)
										constraint += "f_" + fid + "_" + linkSrc.intValue() + "_" + u + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1)
										out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_5: ";
									for (int j = 0; j < adjacencyList[u].size(); j++) // for every other in link of u = (j,u)
									{
										if (feasibleIncomingLinkSrc.contains(adjacencyList[u].elementAt(j).intValue())) continue;
										constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1) out.write(constraint + " = 0\n");

								} else  // non-src and non-dest
								{

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_6: ";
									HashSet<Integer> feasibleOutgoingLinkDst = rackPool.get(u).outgoingLinkDst[f][t];
									for (Integer linkDst :  feasibleOutgoingLinkDst) { // for feasible out link of u = (u,j)
										constraint += "f_" + fid + "_" + u + "_" + linkDst.intValue() + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									constraint += " - ";

									HashSet<Integer> feasibleIncomingLinkSrc = rackPool.get(u).incomingLinkSrc[f][t];
									for (Integer linkSrc : feasibleIncomingLinkSrc) { // for feasible in link of u = (j,u)
										constraint += "f_" + fid + "_" + linkSrc.intValue() + "_" + u + " - ";
										writeCons = 1;
									}
									if (constraint.endsWith("- "))
										constraint = constraint.substring(0, constraint.lastIndexOf("-") - 1);
									if (writeCons == 1) out.write(constraint + " = 0\n");

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_7: ";
									for (int j = 0; j < adjacencyList[u].size(); j++) // for every other out link of u = (u,j)
									{
										if (feasibleOutgoingLinkDst.contains(adjacencyList[u].elementAt(j).intValue())) continue;
										constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1)
										out.write(constraint + " = 0\n");

									writeCons = 0;
									constraint = "c2_" + fid + "_" + u + "_8: ";
									for (int j = 0; j < adjacencyList[u].size(); j++) // for every other in link of u = (j,u)
									{
										if (feasibleIncomingLinkSrc.contains(adjacencyList[u].elementAt(j).intValue())) continue;
										constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " + ";
										writeCons = 1;
									}
									if (constraint.endsWith("+ "))
										constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
									if (writeCons == 1) out.write(constraint + " = 0\n");
								}
							}
							fid++;
						}
					}
					if (f > 0 && f % 20 == 0)
						System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
				}
			}

			// <Constraints of Type 3: Flow >= 0 for any flow on any link
			out.write("\n\\Type 3: Flow >= 0 for any flow on any link\n");
			System.out.println(new Date() + ": Starting part 3");
			constraint = "";

			fid = 0;
			for (int f = 0; f < noNodes; f++) {
				for (int t = 0; t < noNodes; t++) {
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						for (int u = 0; u < noNodes; u++) // for each node u
						{
							for (int j = 0; j < adjacencyList[u].size(); j++) // for each out link of u = (u,j)
							{
								if (!isFlowZero(allFlowIDs[fid], u, adjacencyList[u].elementAt(j).intValue())) {
									constraint = "c3_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + ": ";
									constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " >= 0\n";
									out.write(constraint);
								}
							}

							for (int j = 0; j < adjacencyList[u].size(); j++) // for each in link of u = (j,u)
							{
								if (!isFlowZero(allFlowIDs[fid], adjacencyList[u].elementAt(j).intValue(), u)) {
									constraint = "c3_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + ": ";
									constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u;
									out.write(constraint + " >= 0\n");
								}
							}
						}
						fid++;
					}
				}
			}


			if (!useOptimalRouting && false)
			{
				//<Constraints of Type 4: Flow conservation per path
				System.out.println(new Date() + ": Starting part 4");
				out.write("\n\\Type 4: Flow conservation per path\n");

				fid = 0;
				for (int f = 0; f < noNodes; f++)
				{
					for (int t = 0; t < noNodes; t++)
					{
						if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
						{
							ArrayList<Path> feasiblePaths = pathPool[f][t];
							for (int i=0; i<feasiblePaths.size(); i++) // for each feasible path from f to t for carrying flow fid
							{
								Path thisPath = feasiblePaths.get(i);
								// for each link of this path starting at the second link: the flow on it cannot be smaller than the flow on the previous link
								for (int j=1; j<thisPath.path.size(); j++)
								{
									NPLink currLink = thisPath.path.get(j);
									NPLink prevLink = thisPath.path.get(j-1);
									constraint = "c4_" + fid + "_" + f + "_" + t + "_" + i + "_" + j + ": ";
									constraint += "f_" + fid + "_" + currLink.from + "_" + currLink.to + " - " + "f_" + fid + "_" + prevLink.from + "_" + prevLink.to;
									out.write(constraint + " = 0\n");
								}
							}
							fid++;
						}
					}
					if (f > 0 && f % 20 == 0)
						System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
				}
			}
			
			out.write("End\n");
			out.close();
		}
		catch (Exception e)
		{
			System.err.println("PrintGraphforMCFFairCondensed Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Uses a path-length based heuristic to determine that a certain flow on a certain link will be 0, so no need to factor in LP etc.
	public void PrintGraphforMCFFairCondensedForKnownRoutingEqualShare(String filename, int probability, double[][] switchLevelMatrix, ArrayList<Rack> rackPool, ArrayList<Path>[][] pathPool, ArrayList<LinkUsageTupleWithDuplicate>[][] linksUsageWithDuplicate, NPLink[][] linkPool)
	{
		modifiedFloydWarshall();
		try
		{
			FileWriter fstream = new FileWriter(filename);
			BufferedWriter out = new BufferedWriter(fstream);

			int numFlows = 0;
			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
						numFlows++;

			String file_index = filename.substring(3);
			file_index = file_index.substring(0, file_index.length() - 4);

			System.out.println(file_index + " ***************************** ");

			FlowID[] allFlowIDs = new FlowID[numFlows];
			int curfID=0;
			Writer output1 = new BufferedWriter(new FileWriter("flowIDmap" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
					{
						allFlowIDs[curfID] = new FlowID(curfID, f, t);
						output1.write(curfID + " " + f + " " + t + "\n");
						curfID++;
					}
			output1.close();

			Writer output2 = new BufferedWriter(new FileWriter("linkCaps" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int j=0; j<adjacencyList[f].size(); j++) {  //for each out link of f = (f,j)
					String lType = "";

					if (adjacencyList[f].elementAt(j).linkcapacity > 1) lType = "H-H";
					else lType += adjacencyList[f].size() + "-" + adjacencyList[adjacencyList[f].elementAt(j).intValue()].size();

					output2.write(f + "_" + adjacencyList[f].elementAt(j).intValue() + " " + adjacencyList[f].elementAt(j).linkcapacity + " " + adjacencyList[f].size() + " " + lType + "\n");
				}
			output2.close();

			// Assumed fairness

			// we need to set probability% of flows to large flows.
			// Rand(100) < probability does not give desired distribution for small network sizes
			// Hence, we shuffle the flows and choose the first round(prob% * totalFlows) number of flows
			ArrayList<Integer> list = new ArrayList<Integer>();
			for (int i = 0; i < curfID; i++) {
				list.add(Integer.valueOf(i));
			}
			Collections.shuffle(list);
			float prob = (float) probability / 100;
			int chooseCount = Math.round(prob * curfID);

			//< Objective
			out.write("Maximize \n");
			out.write("obj: ");
			String objective = "K";

			// To make CPLEX not fill pipes as freely as it does while keeping the optimal value same
			// Simple idea: For each utilization of capacity, subtract a tiny amount from the objective.
			// This forces CPLEX to keep the main 'K' part as large as possible, while avoiding wastage of capacity
			out.write(objective);


			// <Constraints of Type 0: fairness i.e. flow >= K
			out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
			System.out.println(new Date() + ": Starting part 0");
			String constraint = "";
			int flowid=0;
			HashMap<FlowSrcDst, Integer> fidMapping = new HashMap<>(); // from <flowSrc, flowDst> to fid
			for (int f = 0; f < noNodes; f++)
			{
				for (int t = 0; t < noNodes; t++)
				{
					if(switchLevelMatrix[f][t]>0) // for each flow flowid with source f and destination t
					{
						constraint = "c0_" + flowid + ": ";
						ArrayList<HopWithDuplicate> outgoingHops = rackPool.get(f).outgoingHopsWithDuplicate[f][t];
						for (HopWithDuplicate hop : outgoingHops) {
							assert(hop.linkSrc == f);
							constraint += "-f_" + flowid + "_" + hop.pid + "_" + f + "_" + hop.linkDst + " ";
						}

						if (list.indexOf(flowid) < chooseCount && probability < 100) {
							constraint += " + " + 10*switchLevelMatrix[f][t] + " K <= 0\n";
						} else {
							constraint += " + " + switchLevelMatrix[f][t] + " K <= 0\n";
						}
						out.write(constraint);

						fidMapping.put(new FlowSrcDst(f, t), flowid);

						flowid++;
					}
				}
			}


			// <Constraints of Type 1: Load on link <= max_load
			out.write("\n\\Type 1: Load on link <= max_load\n");
			System.out.println(new Date() + ": Starting part 1");
			for(int i=0; i<noNodes; i++)
			{
				for (int j=0; j<noNodes; j++)
				{
					if (linksUsageWithDuplicate[i][j].size() > 0) {
						boolean shouldWrite = false;
						constraint = "c1_" + i + "_" + j + ": ";
						ArrayList<LinkUsageTupleWithDuplicate> tuples = linksUsageWithDuplicate[i][j];
						for (LinkUsageTupleWithDuplicate tup : tuples) {
							if (switchLevelMatrix[tup.flowSrc][tup.flowDst] == 0) continue;
							int fid = fidMapping.get(new FlowSrcDst(tup.flowSrc, tup.flowDst));
							constraint += "f_" + fid + "_" + tup.pid + "_" + i + "_" + j + " + ";
							shouldWrite = true;
						}
						if(constraint.endsWith("+ "))
							constraint = constraint.substring(0, constraint.lastIndexOf("+")-1);
						if (shouldWrite) out.write(constraint + " <= " +  linkPool[i][j].linkCapacity + "\n");
					}
				}

				if(i > 0 && i % 20 == 0)
					System.out.println(new Date() + ": "+i+" of "+noNodes+" done");
			}


			// <Constraints of Type 2: Flow conservation at non-source, non-destination
			int LARGE_VALUE = 1; // TOPO_COMPARISON
			System.out.println(new Date() + ": Starting part 2");
			out.write("\n\\Type 2: Flow conservation at non-source, non-destination\n");
			for (int f = 0; f < noNodes; f++) {
				for (int t = 0; t < noNodes; t++) {
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						int fid = fidMapping.get(new FlowSrcDst(f, t));
						for (int u = 0; u < noNodes; u++) // for each node u
						{
							if (u == f) // src
							{
								constraint = "c2_" + fid + "_" + f + "_1: ";
								ArrayList<HopWithDuplicate> outgoingHops = rackPool.get(f).outgoingHopsWithDuplicate[f][t];
								for (HopWithDuplicate hop : outgoingHops) {
									assert(hop.linkSrc == f);
									constraint += "f_" + fid + "_" + hop.pid + "_" + f + "_" + hop.linkDst + " + ";
								}
								if (constraint.endsWith("+ "))
									constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
								out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");
							}
							else if (u == t) // dst
							{
								constraint = "c2_" + fid + "_" + u + "_2: ";
								ArrayList<HopWithDuplicate> incomingHops = rackPool.get(f).incomingHopsWithDuplicate[f][t];
								for (HopWithDuplicate hop : incomingHops) {
									assert(hop.linkDst == t);
									constraint += "f_" + fid + "_" + hop.pid + "_" + hop.linkSrc + "_" + t + " + ";
								}
								if (constraint.endsWith("+ "))
									constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
								out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");
							}
							else  // non-src and non-dest
							{
								boolean shouldWrite = false;
								constraint = "c2_" + fid + "_" + u + "_3: ";
								ArrayList<HopWithDuplicate> outgoingHops = rackPool.get(u).outgoingHopsWithDuplicate[f][t];
								for (HopWithDuplicate hop : outgoingHops) {
									assert(hop.linkSrc == u);
									constraint += "f_" + fid + "_" + hop.pid + "_" + u + "_" + hop.linkDst + " + ";
									shouldWrite = true;
								}
								if (constraint.endsWith("+ "))
									constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
								constraint += " - ";

								ArrayList<HopWithDuplicate> incomingHops = rackPool.get(u).incomingHopsWithDuplicate[f][t];
								for (HopWithDuplicate hop : incomingHops) {
									assert(hop.linkDst == u);
									constraint += "f_" + fid + "_" + hop.pid + "_" + hop.linkSrc + "_" + u + " - ";
									shouldWrite = true;
								}
								if (constraint.endsWith("- "))
									constraint = constraint.substring(0, constraint.lastIndexOf("-") - 1);
								if (shouldWrite) out.write(constraint + " = 0\n");
							}
						}
					}
				}

				if (f > 0 && f % 20 == 0)
					System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
			}


			// <Constraints of Type 3: Flow >= 0 for any flow on any link
			out.write("\n\\Type 3: Flow >= 0 for any flow on any link\n");
			System.out.println(new Date() + ": Starting part 3");
			for (int f = 0; f < noNodes; f++) {
				for (int t = 0; t < noNodes; t++) {
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						int fid = fidMapping.get(new FlowSrcDst(f,t));
						ArrayList<Path> paths = pathPool[f][t];
						for (int i=0; i<paths.size(); i++) {
							Path thisPath = paths.get(i);
							int pid = thisPath.pid;
							for (int l=0; l<thisPath.path.size(); l++) {
								NPLink link = thisPath.path.get(l);
								constraint = "c3_" + fid + "_" + pid + "_" + link.from + "_" + link.to + ": ";
								constraint += "f_" + fid + "_" + pid + "_" + link.from + "_" + link.to + " >= 0\n";
								out.write(constraint);
							}
						}
					}
				}
			}


			//<Constraints of Type 4: Flow conservation per path
			System.out.println(new Date() + ": Starting part 4");
			out.write("\n\\Type 4: Flow conservation per path\n");
			for (int f = 0; f < noNodes; f++)
			{
				for (int t = 0; t < noNodes; t++)
				{
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						int fid = fidMapping.get(new FlowSrcDst(f,t));
						ArrayList<Path> paths = pathPool[f][t];
						for (int i=0; i<paths.size(); i++) {
							Path thisPath = paths.get(i);
							int pid = thisPath.pid;
							for (int l=1; l<thisPath.path.size(); l++) {
								NPLink currLink = thisPath.path.get(l);
								NPLink prevLink = thisPath.path.get(l-1);
								constraint = "c4_" + fid + "_" + pid + "_" + currLink.from + "_" + currLink.to + ": ";
								constraint += "f_" + fid + "_" + pid + "_" + currLink.from + "_" + currLink.to + " -f_" + fid + "_" + pid + "_" + prevLink.from + "_" + prevLink.to + " = 0\n";
								out.write(constraint);
							}
						}
					}
				}

				if (f > 0 && f % 20 == 0)
					System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
			}


//			//<Constraints of Type 5: Equal share
//			System.out.println(new Date() + ": Starting part 5");
//			out.write("\n\\Type 5: Equal share\n");
//			for (int f = 0; f < noNodes; f++)
//			{
//				for (int t = 0; t < noNodes; t++)
//				{
//					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
//					{
//						int fid = fidMapping.get(new FlowSrcDst(f,t));
//						ArrayList<HopWithDuplicate> outgoingHops = rackPool.get(f).outgoingHopsWithDuplicate[f][t];
//						for (int i=1; i<outgoingHops.size(); i++) {
//							HopWithDuplicate currHop = outgoingHops.get(i);
//							HopWithDuplicate prevHop = outgoingHops.get(i-1);
//							assert(currHop.linkSrc == f);
//							assert(prevHop.linkSrc == f);
//							constraint = "c5_" + fid + "_" + currHop.pid + "_" + f + "_" + currHop.linkDst + ": ";
//							constraint += "f_" + fid + "_" + currHop.pid + "_" + f + "_" + currHop.linkDst + " -f_" + fid + "_" + prevHop.pid + "_" + f + "_" + prevHop.linkDst + " = 0\n";
//							out.write(constraint);
//						}
//					}
//				}
//
//				if (f > 0 && f % 20 == 0)
//					System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
//			}


			out.write("End\n");
			out.close();
		}
		catch (Exception e)
		{
			System.err.println("PrintGraphforMCFFairCondensed Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void PrintGraphforMCFFairCondensedForKnownRoutingUnequalShare(String filename, int probability, double[][] switchLevelMatrix, ArrayList<Rack> rackPool, ArrayList<Path>[][] pathPool, HashSet<LinkUsageTupleWithNoDuplicate>[][] linksUsageWithNoDuplicate, NPLink[][] linkPool)
	{
		modifiedFloydWarshall();
		try
		{
			FileWriter fstream = new FileWriter(filename);
			BufferedWriter out = new BufferedWriter(fstream);

			int numFlows = 0;
			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
						numFlows++;

			String file_index = filename.substring(3);
			file_index = file_index.substring(0, file_index.length() - 4);

			System.out.println(file_index + " ***************************** ");

			FlowID[] allFlowIDs = new FlowID[numFlows];
			int curfID=0;
			Writer output1 = new BufferedWriter(new FileWriter("flowIDmap" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
					{
						allFlowIDs[curfID] = new FlowID(curfID, f, t);
						output1.write(curfID + " " + f + " " + t + "\n");
						curfID++;
					}
			output1.close();

			Writer output2 = new BufferedWriter(new FileWriter("linkCaps" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int j=0; j<adjacencyList[f].size(); j++) {  //for each out link of f = (f,j)
					String lType = "";

					if (adjacencyList[f].elementAt(j).linkcapacity > 1) lType = "H-H";
					else lType += adjacencyList[f].size() + "-" + adjacencyList[adjacencyList[f].elementAt(j).intValue()].size();

					output2.write(f + "_" + adjacencyList[f].elementAt(j).intValue() + " " + adjacencyList[f].elementAt(j).linkcapacity + " " + adjacencyList[f].size() + " " + lType + "\n");
				}
			output2.close();

			// Assumed fairness

			// we need to set probability% of flows to large flows.
			// Rand(100) < probability does not give desired distribution for small network sizes
			// Hence, we shuffle the flows and choose the first round(prob% * totalFlows) number of flows
			ArrayList<Integer> list = new ArrayList<Integer>();
			for (int i = 0; i < curfID; i++) {
				list.add(Integer.valueOf(i));
			}
			Collections.shuffle(list);
			float prob = (float) probability / 100;
			int chooseCount = Math.round(prob * curfID);

			//< Objective
			out.write("Maximize \n");
			out.write("obj: ");
			String objective = "K";

			// To make CPLEX not fill pipes as freely as it does while keeping the optimal value same
			// Simple idea: For each utilization of capacity, subtract a tiny amount from the objective.
			// This forces CPLEX to keep the main 'K' part as large as possible, while avoiding wastage of capacity
			out.write(objective);


			// <Constraints of Type 0: fairness i.e. flow >= K
			out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
			System.out.println(new Date() + ": Starting part 0");
			String constraint = "";
			int flowid=0;
			HashMap<FlowSrcDst, Integer> fidMapping = new HashMap<>(); // from <flowSrc, flowDst> to fid
			for (int f = 0; f < noNodes; f++)
			{
				for (int t = 0; t < noNodes; t++)
				{
					if(switchLevelMatrix[f][t]>0) // for each flow flowid with source f and destination t
					{
						constraint = "c0_" + flowid + ": ";
						HashSet<HopWithNoDuplicate> outgoingHops = rackPool.get(f).outgoingHopsWithNoDuplicate[f][t];
						for (HopWithNoDuplicate hop : outgoingHops) {
							assert(hop.linkSrc == f);
							constraint += "-f_" + flowid + "_" + hop.pid + "_" + f + "_" + hop.linkDst + " ";
						}

						if (list.indexOf(flowid) < chooseCount && probability < 100) {
							constraint += " + " + 10*switchLevelMatrix[f][t] + " K <= 0\n";
						} else {
							constraint += " + " + switchLevelMatrix[f][t] + " K <= 0\n";
						}
						out.write(constraint);

						fidMapping.put(new FlowSrcDst(f, t), flowid);

						flowid++;
					}
				}
			}


			// <Constraints of Type 1: Load on link <= max_load
			out.write("\n\\Type 1: Load on link <= max_load\n");
			System.out.println(new Date() + ": Starting part 1");
			for(int i=0; i<noNodes; i++)
			{
				for (int j=0; j<noNodes; j++)
				{
					if (linksUsageWithNoDuplicate[i][j].size() > 0) {
						boolean shouldWrite = false;
						constraint = "c1_" + i + "_" + j + ": ";
						HashSet<LinkUsageTupleWithNoDuplicate> tuples = linksUsageWithNoDuplicate[i][j];
						for (LinkUsageTupleWithNoDuplicate tup : tuples) {
							if (switchLevelMatrix[tup.flowSrc][tup.flowDst] == 0) continue;
							int fid = fidMapping.get(new FlowSrcDst(tup.flowSrc, tup.flowDst));
							constraint += "f_" + fid + "_" + tup.pid + "_" + i + "_" + j + " + ";
							shouldWrite = true;
						}
						if(constraint.endsWith("+ "))
							constraint = constraint.substring(0, constraint.lastIndexOf("+")-1);
						if (shouldWrite) out.write(constraint + " <= " +  linkPool[i][j].linkCapacity + "\n");
					}
				}

				if(i > 0 && i % 20 == 0)
					System.out.println(new Date() + ": "+i+" of "+noNodes+" done");
			}


			// <Constraints of Type 2: Flow conservation at non-source, non-destination
			int LARGE_VALUE = 1; // TOPO_COMPARISON
			System.out.println(new Date() + ": Starting part 2");
			out.write("\n\\Type 2: Flow conservation at non-source, non-destination\n");
			for (int f = 0; f < noNodes; f++) {
				for (int t = 0; t < noNodes; t++) {
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						int fid = fidMapping.get(new FlowSrcDst(f, t));
						for (int u = 0; u < noNodes; u++) // for each node u
						{
							if (u == f) // src
							{
								constraint = "c2_" + fid + "_" + f + "_1: ";
								HashSet<HopWithNoDuplicate> outgoingHops = rackPool.get(f).outgoingHopsWithNoDuplicate[f][t];
								for (HopWithNoDuplicate hop : outgoingHops) {
									assert(hop.linkSrc == f);
									constraint += "f_" + fid + "_" + hop.pid + "_" + f + "_" + hop.linkDst + " + ";
								}
								if (constraint.endsWith("+ "))
									constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
								out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");
							}
							else if (u == t) // dst
							{
								constraint = "c2_" + fid + "_" + u + "_2: ";
								HashSet<HopWithNoDuplicate> incomingHops = rackPool.get(f).incomingHopsWithNoDuplicate[f][t];
								for (HopWithNoDuplicate hop : incomingHops) {
									assert(hop.linkDst == t);
									constraint += "f_" + fid + "_" + hop.pid + "_" + hop.linkSrc + "_" + t + " + ";
								}
								if (constraint.endsWith("+ "))
									constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
								out.write(constraint + " <= " + switchLevelMatrix[f][t] * LARGE_VALUE + "\n");
							}
							else  // non-src and non-dest
							{
								boolean shouldWrite = false;
								constraint = "c2_" + fid + "_" + u + "_3: ";
								HashSet<HopWithNoDuplicate> outgoingHops = rackPool.get(u).outgoingHopsWithNoDuplicate[f][t];
								for (HopWithNoDuplicate hop : outgoingHops) {
									assert(hop.linkSrc == u);
									constraint += "f_" + fid + "_" + hop.pid + "_" + u + "_" + hop.linkDst + " + ";
									shouldWrite = true;
								}
								if (constraint.endsWith("+ "))
									constraint = constraint.substring(0, constraint.lastIndexOf("+") - 1);
								constraint += " - ";

								HashSet<HopWithNoDuplicate> incomingHops = rackPool.get(u).incomingHopsWithNoDuplicate[f][t];
								for (HopWithNoDuplicate hop : incomingHops) {
									assert(hop.linkDst == u);
									constraint += "f_" + fid + "_" + hop.pid + "_" + hop.linkSrc + "_" + u + " - ";
									shouldWrite = true;
								}
								if (constraint.endsWith("- "))
									constraint = constraint.substring(0, constraint.lastIndexOf("-") - 1);
								if (shouldWrite) out.write(constraint + " = 0\n");
							}
						}
					}
				}

				if (f > 0 && f % 20 == 0)
					System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
			}


			// <Constraints of Type 3: Flow >= 0 for any flow on any link
			out.write("\n\\Type 3: Flow >= 0 for any flow on any link\n");
			System.out.println(new Date() + ": Starting part 3");
			for (int f = 0; f < noNodes; f++) {
				for (int t = 0; t < noNodes; t++) {
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						int fid = fidMapping.get(new FlowSrcDst(f,t));
						ArrayList<Path> paths = pathPool[f][t];
						for (int i=0; i<paths.size(); i++) {
							Path thisPath = paths.get(i);
							int pid = thisPath.pid;
							for (int l=0; l<thisPath.path.size(); l++) {
								NPLink link = thisPath.path.get(l);
								constraint = "c3_" + fid + "_" + pid + "_" + link.from + "_" + link.to + ": ";
								constraint += "f_" + fid + "_" + pid + "_" + link.from + "_" + link.to + " >= 0\n";
								out.write(constraint);
							}
						}
					}
				}
			}


			//<Constraints of Type 4: Flow conservation per path
			System.out.println(new Date() + ": Starting part 4");
			out.write("\n\\Type 4: Flow conservation per path\n");
			for (int f = 0; f < noNodes; f++)
			{
				for (int t = 0; t < noNodes; t++)
				{
					if (switchLevelMatrix[f][t] > 0) // for each flow fid from f to t
					{
						int fid = fidMapping.get(new FlowSrcDst(f,t));
						ArrayList<Path> paths = pathPool[f][t];
						for (int i=0; i<paths.size(); i++) {
							Path thisPath = paths.get(i);
							int pid = thisPath.pid;
							for (int l=1; l<thisPath.path.size(); l++) {
								NPLink currLink = thisPath.path.get(l);
								NPLink prevLink = thisPath.path.get(l-1);
								constraint = "c4_" + fid + "_" + pid + "_" + currLink.from + "_" + currLink.to + ": ";
								constraint += "f_" + fid + "_" + pid + "_" + currLink.from + "_" + currLink.to + " -f_" + fid + "_" + pid + "_" + prevLink.from + "_" + prevLink.to + " = 0\n";
								out.write(constraint);
							}
						}
					}
				}

				if (f > 0 && f % 20 == 0)
					System.out.println(new Date() + ": " + f + " of " + noNodes + " done");
			}


			out.write("End\n");
			out.close();
		}
		catch (Exception e)
		{
			System.err.println("PrintGraphforMCFFairCondensed Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void PrintSimpleGraph(String filename, double[][] switchLevelMatrix)
	{
		modifiedFloydWarshall();
		int r=noNodes; //# of ToR switches
		int svrs=totalWeight;

		try
		{
			FileWriter fstream = new FileWriter(filename);
			BufferedWriter out = new BufferedWriter(fstream);
			
			int numEdges = 0;
			for(int i=0; i<noNodes; i++)
			{
				numEdges += adjacencyList[i].size();
			}


			int edgeID = 0;
			int edgeCapacity = 1;
			for(int i=0; i<noNodes; i++)
			{
				for(int j=0; j<adjacencyList[i].size(); j++)
				{
					edgeID++;
				}

			}

			System.out.println("Number of edges " + edgeID);


			int numFlows = 0;
			for (int f = 0; f < noNodes; f++){			
				for (int t = 0; t < noNodes; t++){
					if(switchLevelMatrix[f][t]>0){
						numFlows++;
					}
				}
			}


			int fid=0;
			String constraint = "";

			out.write("Maximize \n");
			out.write("obj: ");
			String objective = "K";
			out.write(objective);

			//Type 0 - Flows >= K 
			out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
			System.out.println(new Date() + ": Starting part 0");

			for (int f = 0; f < noNodes; f++){
				for (int t = 0; t < noNodes; t++){
		 			if(switchLevelMatrix[f][t]>0){
							constraint = "c0_" + fid + ": ";
							constraint += "- f_" + f + "_" + t +  " ";
							constraint += " + " + switchLevelMatrix[f][t] + " K <= 0\n";
							out.write(constraint);
							fid++;
				//			System.out.println("fid " + fid + " f " + f + " t " + t + " flows " + switchLevelMatrix[f][t]);
						}
				}
			}


			//Type 1 - sum (l_i_j_d) over d on link i_j less than link capacity
			out.write("\n\\Type 1: Link capacity constraint\n");
                        System.out.println(new Date() + ": Starting part 1");

                       	for(int i=0; i<noNodes; i++){
                                for(int j=0; j<adjacencyList[i].size(); j++){
					constraint = "c2_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + ": ";
                                        for(int k=0; k<noNodes; k++){
                                                constraint += " + l_"+ i + "_" + adjacencyList[i].elementAt(j).intValue() +"_" + k ;
                                        }
					constraint += " <= " +  adjacencyList[i].elementAt(j).linkcapacity + "\n";
					out.write(constraint);
                                }
                        }

			//Type 2 - flow conservation at nodes
			out.write("\n\\Type 2: Flow conservation at node\n");
			System.out.println(new Date() + ": Starting part 2");

			for (int i = 0; i < noNodes; i++) {
				for (int k = 0; k < noNodes; k++) {
					if (i != k) {
						constraint = "c3_" + i + "_" + k + ": ";
						if (switchLevelMatrix[i][k] > 0) {
							constraint += " f_" + i + "_" + k;

						}

						for (int j = 0; j < adjacencyList[i].size(); j++) {
							constraint += " + l_" + adjacencyList[i].elementAt(j).intValue() + "_" + i + "_" + k + " ";
						}

						for (int j = 0; j < adjacencyList[i].size(); j++) {
							constraint += " - l_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + "_" + k + " ";
						}

						constraint += " = 0\n";
						out.write(constraint);
					} else if (i == k) {
						constraint = "c3_" + i + "_" + k + ": ";
						for (int j = 0; j < noNodes; j++) {
							if (switchLevelMatrix[j][i] > 0) {
								constraint += " - f_" + j + "_" + i;
							}
						}

						for (int j = 0; j < adjacencyList[i].size(); j++) {
							constraint += " + l_" + adjacencyList[i].elementAt(j).intValue() + "_" + i + "_" + i + " ";
						}
						constraint += " = 0\n";
						out.write(constraint);

					}
				}
			}

			out.close();
		}
		catch (Exception e)
		{
			System.err.println("PrintSimpleGraph Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/*
	// Uses a path-length based heuristic to determine that a certain flow on a certain link will be 0, so no need to factor in LP etc.
	public void PrintGraphforMCFFairCondensed(String filename, int trafficmode, int probability)
	{

		modifiedFloydWarshall();
		int r=noNodes; //# of ToR switches
		int svrs=totalWeight;

		int nflowlet = 1;
		try
		{
			// traffic-mode: 0 = randPerm; 1 = all-all; 2 = all-to-one; Any higher n means stride(n)
			ArrayList<TrafficPair> rndmap;
			if (trafficmode == 0) {rndmap = RandomPermutationPairs(svrs); System.out.println("RAND-PERM TRAFFIC ************");}
			else if (trafficmode == 1) {rndmap = TrafficGenAllAll(); System.out.println("All-all *********************");}
			else if (trafficmode == 2) rndmap = TrafficGenAllToOne();
			else rndmap = TrafficGenStride(trafficmode);

			FileWriter fstream = new FileWriter(filename);
			BufferedWriter out = new BufferedWriter(fstream);

			// Nodes
			int numEdges = 0;
			for(int i=0; i<noNodes; i++)
			{
				numEdges += adjacencyList[i].size();
			}

			// Edges
			int edgeID = 0;
			int edgeCapacity = 1;
			for(int i=0; i<noNodes; i++)
			{
				for(int j=0; j<adjacencyList[i].size(); j++)
				{
					int to = adjacencyList[i].elementAt(j).intValue();
					edgeID++;
				}
			}

			for (int i = 0; i < rndmap.size(); i++) {
				int from = rndmap.get(i).from;
				int to=rndmap.get(i).to;

				int fromsw=svrToSwitch(from);
				int tosw=svrToSwitch(to);

				// I'm counting only number of connections
				if (fromsw == tosw) continue;
				if (switchLevelMatrix[fromsw][tosw] == 0) nCommodities ++;
				switchLevelMatrix[fromsw][tosw] ++;
			}


			// Commodities
			int commodityIndex = 0;
			for (int f = 0; f < noNodes; f ++)
				for (int t = 0; t < noNodes; t++)
					if (switchLevelMatrix[f][t] != 0) commodityIndex ++;


			int numFlows = 0;
			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
						numFlows++;

			String file_index = filename.substring(3);
			file_index = file_index.substring(0, file_index.length() - 4);

			System.out.println(file_index + " ***************************** ");

			FlowID[] allFlowIDs = new FlowID[numFlows];
			int curfID=0;
			Writer output1 = new BufferedWriter(new FileWriter("flowIDmap" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int t = 0; t < noNodes; t++)
					if(switchLevelMatrix[f][t]>0)
					{
						allFlowIDs[curfID] = new FlowID(curfID, f, t);
						output1.write(curfID + " " + f + " " + t + "\n");
						curfID++;
					}
			output1.close();

			Writer output2 = new BufferedWriter(new FileWriter("linkCaps" + file_index));

			for (int f = 0; f < noNodes; f++)
				for (int j=0; j<adjacencyList[f].size(); j++) {  //for each out link of f = (f,j)
					String lType = "";

					if (adjacencyList[f].elementAt(j).linkcapacity > 1) lType = "H-H";
					else lType += adjacencyList[f].size() + "-" + adjacencyList[adjacencyList[f].elementAt(j).intValue()].size();

					output2.write(f + "_" + adjacencyList[f].elementAt(j).intValue() + " " + adjacencyList[f].elementAt(j).linkcapacity + " " + adjacencyList[f].size() + " " + lType + "\n");
				}
			output2.close();

			//boolean fair = false;
			boolean fair = true;
			int fid=0;
			String constraint = "";
			if (fair) {
				// we need to set probability% of flows to large flows.
				// Rand(100) < probability does not give desired distribution for small network sizes
				// Hence, we shuffle the flows and choose the first round(prob% * totalFlows) number of flows
				ArrayList<Integer> list = new ArrayList<Integer>();
				for (int i = 0; i < curfID; i++) {
					list.add(new Integer(i));
				}
				Collections.shuffle(list);
				float prob = (float) probability / 100;
				int chooseCount = Math.round(prob * curfID);

				//< Objective
				out.write("Maximize \n");
				out.write("obj: ");
				String objective = "K";

				// To make CPLEX not fill pipes as freely as it does while keeping the optimal value same
				// Simple idea: For each utilization of capacity, subtract a tiny amount from the objective.
				// This forces CPLEX to keep the main 'K' part as large as possible, while avoiding wastage of capacity
//				for (int f = 0; f < noNodes; f++)
//				  for (int j=0; j<adjacencyList[f].size(); j++) {  //for each out link of f = (f,j)
//				  for (int fid = 0; fid < numFlows; fid ++) {
//				  if (!isFlowZero(allFlowIDs[fid], f, adjacencyList[f].elementAt(j).intValue())) {
//				  double normalized_factor = 0.00000001 / adjacencyList[f].elementAt(j).linkcapacity;
//				  objective += " -" + (new BigDecimal(Double.toString(normalized_factor))).toPlainString() + "f_" + fid + "_" + f + "_" + adjacencyList[f].elementAt(j).intValue();
//				  }
//				  }
//				  }

				out.write(objective);


				//<Constraints of Type 0: fairness i.e. flow >= K
				out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
				System.out.println(new Date() + ": Starting part 0");
				for (int f = 0; f < noNodes; f++)
				{
					for (int t = 0; t < noNodes; t++)
					{
						if(switchLevelMatrix[f][t]>0)	  //for each flow fid with source f
						{
							constraint = "c0_" + fid + ": ";
							//System.out.println("CHECK FLOW =========== " + fid + " " + f + " " + t);

							int writeCons = 0;
							for(int j=0; j<adjacencyList[f].size(); j++)   //for each out link of f = (f,j)
							{
								if (!isFlowZero(allFlowIDs[fid], f, adjacencyList[f].elementAt(j).intValue()))
								{
									constraint += "-f_" + fid + "_" + f + "_" + adjacencyList[f].elementAt(j).intValue() + " ";
									writeCons = 1;
								}
								//if(j!=adjacencyList[f].size()-1) constraint += "- ";
							}
							if (writeCons == 1)
							{
								Random rand = new Random();
								if (list.indexOf(fid) < chooseCount && probability < 100) {
									constraint += " + " + 10*switchLevelMatrix[f][t] + " K <= 0\n";
								} else {
									constraint += " + " + switchLevelMatrix[f][t] + " K <= 0\n";
								}
								out.write(constraint);
							}
							fid++;
						}
					}
				}
			}
			else { // no fairness constraints -- max total throughput
				//< Objective
				out.write("Maximize \n");
				out.write("obj: ");
				fid = 0;
				String objective = "";
				for (int f = 0; f < noNodes; f++) {
					for (int t = 0; t < noNodes; t++) {
						if(switchLevelMatrix[f][t]>0)   { //for each flow fid with source f
							for(int j=0; j<adjacencyList[f].size(); j++) { //for each out link of f = (f,j)
								objective += "f_" + fid + "_" + f + "_" + adjacencyList[f].elementAt(j).intValue() + " ";
								if(j!=adjacencyList[f].size()-1)
									objective += "+ ";
							}
							if(fid != commodityIndex-1)
								objective += "+ ";
							else
								objective += "\n";
							fid++;
						}
					}
				}
				out.write(objective);
				out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
				//>
			}

			//<Constraints of Type 1: Load on link <= max_load
			out.write("\n\\Type 1: Load on link <= max_load\n");
			System.out.println(new Date() + ": Starting part 1");
			constraint = "";
			int strCapacity = 25*commodityIndex;
			for(int i=0; i<noNodes; i++)
			{
				for(int j=0; j<adjacencyList[i].size(); j++)
				{
					StringBuilder curConstraint = new StringBuilder(strCapacity);
					int writeCons = 0;
					for(int fd_=0; fd_<commodityIndex; fd_++)
					{
						//for each flow fd_
						if (!isFlowZero(allFlowIDs[fd_], i, adjacencyList[i].elementAt(j).intValue()))
						{
							//constraint += "f_" + fd_ + "_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + " + ";
							curConstraint.append("f_" + fd_ + "_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + " + ");
							writeCons = 1;
						}
					}
					constraint = curConstraint.toString();
					if(constraint.endsWith("+ "))
						constraint = constraint.substring(0, constraint.lastIndexOf("+")-1);
					//System.out.println("string size: "+constraint.length());
					if(writeCons == 1)
					{
						out.write("c1_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + ": " + constraint + " <= " +  adjacencyList[i].elementAt(j).linkcapacity + "\n");
						//constraint = "";
					}
				}
				if(i > 0 && i % 20 == 0)
					System.out.println(new Date() + ": "+i+" of "+noNodes+" done");
			}
			//>

			//<Constraints of Type 2: Flow conservation at non-source, non-destination
			int LARGE_VALUE = 10;		// TOPO_COMPARISON
			System.out.println(new Date() + ": Starting part 2");
			out.write("\n\\Type 2: Flow conservation at non-source, non-destination\n");
			fid=0;
			for (int f = 0; f < noNodes; f++)
			{
				for (int t = 0; t < noNodes; t++)
				{
					if(switchLevelMatrix[f][t]>0)	   //for each flow fid
					{
						for(int u=0; u<noNodes; u++)   //for each node u
						{
							constraint = "";
							int writeCons = 0;
							if(u==f)    //src
							{
								constraint = "c2_" + fid + "_" + u + "_1: ";

								for(int j=0; j<adjacencyList[u].size(); j++)   //for each out link of u = (u,j)
								{
									if (!isFlowZero(allFlowIDs[fid], u, adjacencyList[u].elementAt(j).intValue()))
									{
										constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
										writeCons = 1;
									}
								}
								if (constraint.endsWith("+ ")) constraint = constraint.substring(0, constraint.lastIndexOf("+")-1 );
								if (writeCons == 1) out.write(constraint + " <= " + switchLevelMatrix[f][t]*LARGE_VALUE + "\n");
								writeCons = 0;
								constraint = "c2_" + fid + "_" + u + "_2: ";
								for(int j=0; j<adjacencyList[u].size(); j++)   //for each in link of u = (j,u)
								{
									if (!isFlowZero(allFlowIDs[fid], adjacencyList[u].elementAt(j).intValue(), u))
									{
										constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " + ";
										writeCons = 1;
									}
								}
								if (constraint.endsWith("+ ")) constraint = constraint.substring(0, constraint.lastIndexOf("+")-1 );
								if (writeCons == 1) out.write(constraint + " = 0\n");
							}
							else if (u==t) {}
							else  // non-src and non-dest
							{
								constraint = "c2_" + fid + "_" + u + "_3: ";
								for(int j=0; j<adjacencyList[u].size(); j++)   //for each out link of u = (u,j)
								{
									if (!isFlowZero(allFlowIDs[fid], u, adjacencyList[u].elementAt(j).intValue()))
									{
										constraint += "f_" + fid + "_" + u + "_" + adjacencyList[u].elementAt(j).intValue() + " + ";
										writeCons = 1;
									}
								}
								if (constraint.endsWith("+ ")) constraint = constraint.substring(0, constraint.lastIndexOf("+")-1 );
								constraint += " - ";

								for(int j=0; j<adjacencyList[u].size(); j++)   //for each in link of u = (j,u)
								{
									if (!isFlowZero(allFlowIDs[fid], adjacencyList[u].elementAt(j).intValue(), u))
									{
										constraint += "f_" + fid + "_" + adjacencyList[u].elementAt(j).intValue() + "_" + u + " - ";
										writeCons = 1;
									}
								}
								if (constraint.endsWith("- ")) constraint = constraint.substring(0, constraint.lastIndexOf("-")-1 );
								if (writeCons == 1) out.write(constraint + " = 0\n");
							}
						}
						fid++;
					}
				}
				if(f > 0 && f % 20 == 0)
					System.out.println(new Date() + ": "+f+" of "+noNodes+" done");
			}

			out.write("End\n");
			out.close();
		}
		catch (Exception e)
		{
			System.err.println("PrintGraphforMCFFairCondensed Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void PrintSimpleGraph(String filename, int trafficmode)
	{
		modifiedFloydWarshall();
		int r=noNodes; //# of ToR switches
		int svrs=totalWeight;

		try
		{
			// traffic-mode: 0 = randPerm; 1 = all-all; 2 = all-to-one; Any higher n means stride(n)
			ArrayList<TrafficPair> rndmap;
			if (trafficmode == 0) {rndmap = RandomPermutationPairs(svrs); System.out.println("RAND-PERM TRAFFIC ************");}
			else if (trafficmode == 1) {rndmap = TrafficGenAllAll(); System.out.println("All-all *********************");}
			else if (trafficmode == 2) rndmap = TrafficGenAllToOne();
			else rndmap = TrafficGenStride(trafficmode);

			FileWriter fstream = new FileWriter(filename);
			BufferedWriter out = new BufferedWriter(fstream);

			int numEdges = 0;
			for(int i=0; i<noNodes; i++)
			{
				numEdges += adjacencyList[i].size();
			}



			int edgeID = 0;
			int edgeCapacity = 1;
			for(int i=0; i<noNodes; i++)
			{
				for(int j=0; j<adjacencyList[i].size(); j++)
				{
					edgeID++;
				}

			}

			System.out.println("Number of edges " + edgeID);
			System.out.println("Map size " + rndmap.size());

			for (int i = 0; i < rndmap.size(); i++) {
				int from = rndmap.get(i).from;
				int to=rndmap.get(i).to;

				int fromsw=svrToSwitch(from);
				int tosw=svrToSwitch(to);

				// I'm counting only number of connections
				//if (switchLevelMatrix[fromsw][tosw] != 0) nCommodities ++;

				if (fromsw == tosw) continue;

				if (switchLevelMatrix[fromsw][tosw] == 0) nCommodities ++;

				switchLevelMatrix[fromsw][tosw] ++;

			}


			int numFlows = 0;
			for (int f = 0; f < noNodes; f++){
				for (int t = 0; t < noNodes; t++){
					if(switchLevelMatrix[f][t]>0){
						numFlows++;
					}
				}
			}


			int fid=0;
			String constraint = "";

			out.write("Maximize \n");
			out.write("obj: ");
			String objective = "K";
			out.write(objective);

			//Type 0 - Flows >= K
			out.write("\n\nSUBJECT TO \n\\Type 0: Flow >= K\n");
			System.out.println(new Date() + ": Starting part 0");

			for (int f = 0; f < noNodes; f++){
				for (int t = 0; t < noNodes; t++){
					if(switchLevelMatrix[f][t]>0){
						constraint = "c0_" + fid + ": ";
						constraint += "- f_" + f + "_" + t +  " ";
						constraint += " + " + switchLevelMatrix[f][t] + " K <= 0\n";
						out.write(constraint);
						fid++;
						//			System.out.println("fid " + fid + " f " + f + " t " + t + " flows " + switchLevelMatrix[f][t]);
					}
				}
			}


			//Type 1 - sum (l_i_j_d) over d on link i_j less than link capacity
			out.write("\n\\Type 1: Link capacity constraint\n");
			System.out.println(new Date() + ": Starting part 1");

			for(int i=0; i<noNodes; i++){
				for(int j=0; j<adjacencyList[i].size(); j++){
					constraint = "c2_" + i + "_" + adjacencyList[i].elementAt(j).intValue() + ": ";
					for(int k=0; k<noNodes; k++){
						constraint += " + l_"+ i + "_" + adjacencyList[i].elementAt(j).intValue() +"_" + k ;
					}
					constraint += " <= " +  adjacencyList[i].elementAt(j).linkcapacity + "\n";
					out.write(constraint);
				}
			}

			//Type 2 - flow conservation at nodes
			out.write("\n\\Type 2: Flow conservation at node\n");
			System.out.println(new Date() + ": Starting part 2");

			for(int i=0; i<noNodes; i++){
				for(int k=0; k<noNodes; k++){
					if (i!=k){
						constraint = "c3_" + i + "_" + k + ": ";
						if (switchLevelMatrix[i][k] > 0){
							constraint += " f_" + i + "_" + k  ;

						}

						for(int j=0; j<adjacencyList[i].size(); j++){
							constraint += " + l_" + adjacencyList[i].elementAt(j).intValue() +"_" + i +"_" + k + " ";
						}

						for(int j=0; j<adjacencyList[i].size(); j++){
							constraint += " - l_" + i +"_" + adjacencyList[i].elementAt(j).intValue() +"_" + k + " ";
						}

						constraint+= " = 0\n";
						out.write(constraint);
					}
					else if (i == k) {
						constraint = "c3_" + i + "_" + k + ": ";
						for(int j=0; j<noNodes; j++){
							if(switchLevelMatrix[j][i] > 0){
								constraint += " - f_" + j + "_" + i;
							}
						}

						for(int j=0; j<adjacencyList[i].size(); j++){
							constraint += " + l_" + adjacencyList[i].elementAt(j).intValue() +"_" + i +"_" + i + " ";
						}
						constraint+= " = 0\n";
						out.write(constraint);

					}
				}
			}

			out.close();
		}
		catch (Exception e)
		{
			System.err.println("PrintSimpleGraph Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	*/

    //Get distances between servers
    public void printServerDistance(String serverDistFile)
    {
        try
        {
            modifiedFloydWarshall();
            FileWriter fstream = new FileWriter(serverDistFile);
            BufferedWriter out = new BufferedWriter(fstream);

            for (int i = 0 ; i < noNodes; i++)
            {
                for (int j = 0 ; j < noNodes; j++)
                {
                    if (weightEachNode[i] > 0 && weightEachNode[j] > 0 && i != j)
                    {
                        for (int node_i_svrs = 0; node_i_svrs < weightEachNode[i]; node_i_svrs++)
                        {
                            for (int node_j_svrs = 0; node_j_svrs < weightEachNode[j]; node_j_svrs++)
                            {
                                int server1 = weightBeforeEachNode[i] + node_i_svrs;
                                int server2 = weightBeforeEachNode[j] + node_j_svrs;
                                int printWeight = shortestPathLen[i][j] + 2;
                                out.write(server1 + " " + server2 + " " + printWeight + "\n");
                            }
                        }
                    }
                }
            }
            out.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
