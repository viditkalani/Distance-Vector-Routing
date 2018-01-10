import javax.swing.*;

public class RouterNode {
    private int myID;
    private GuiTextArea myGUI;
    private RouterSimulator sim;
    private int[] costs = new int[RouterSimulator.NUM_NODES];

    // Table of costs from x to y (current belief of the node, not global)
    private int[][] routercosts = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES];

    // Next hop on route to target node
    private int[] routes = new int[RouterSimulator.NUM_NODES];

    private int[][] table = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES];

    //--------------------------------------------------
    public RouterNode(int ID, RouterSimulator sim, int[] costs) {
        myID = ID;
        this.sim = sim;
        myGUI =new GuiTextArea("  Output window for Router #"+ ID + "  ");

        // Set my own link costs
        System.arraycopy(costs, 0, this.costs, 0, RouterSimulator.NUM_NODES);

        // Initialize array with costs from x to y with infinity
        for(int i = 0; i < RouterSimulator.NUM_NODES; i++)
        {
                // Also init routes
                routes[i] = i;

                for(int j = 0; j < RouterSimulator.NUM_NODES; j++)
                {
                        routercosts[i][j] = RouterSimulator.INFINITY;
                }
        }

        // Set costs for this node to link costs in cost array
        System.arraycopy(costs, 0, this.routercosts[myID], 0, RouterSimulator.NUM_NODES);

        printDistanceTable();

        // Send update with own costs to neighbors
        for(int i = 0; i < RouterSimulator.NUM_NODES; i++)
        {
            RouterPacket pk = new RouterPacket(myID, i, costs);
            sendUpdate(pk);
        }
    }

    //--------------------------------------------------
    private boolean recalcRoutes()
    {
        boolean bUpdated = false;
        for(int to_node_x = 0; to_node_x < RouterSimulator.NUM_NODES; ++to_node_x)
        {
                // Do not recalculate route to self
                if(to_node_x == myID)
                {
                        continue;
                }

                int min_cost_to_x = RouterSimulator.INFINITY;
                int via_neighbor_y = routes[to_node_x];

                for(int neighbor_y = 0; neighbor_y < RouterSimulator.NUM_NODES; ++neighbor_y)
                {
                        // Do not consider own costs for minimum cost calculations. 
                        // Only check reachable neighbors
                        if((neighbor_y == myID) || (this.costs[neighbor_y] == RouterSimulator.INFINITY))
                        {
                                continue;
                        }

                        // Get min cost of all neighbors y to node x
                        // Cost of neighbor to x + link cost to neighbor
                        if(routercosts[neighbor_y][to_node_x] + this.costs[neighbor_y] < min_cost_to_x)
                        {
                                min_cost_to_x = routercosts[neighbor_y][to_node_x] + 
                                                this.costs[neighbor_y];
                                via_neighbor_y = neighbor_y;
                        }
                }

                if(min_cost_to_x != routercosts[myID][to_node_x])
                {
                        // Update my table and propagate update to others
                        routercosts[myID][to_node_x] = min_cost_to_x;
                        routes[to_node_x] = via_neighbor_y;
                        bUpdated = true;
                }
        }

        return bUpdated;
    }

    private void sendUpdateToNeighbors()
    {
        boolean reversePoisoning = true;

        for(int nb_y = 0; nb_y < RouterSimulator.NUM_NODES; ++nb_y)
        {
            RouterPacket pk = new RouterPacket(myID, nb_y, routercosts[myID]);

            if(reversePoisoning)
            {
                // For all targets, check if we are routing through neighbor y -> poison reverse
                for(int x = 0; x < RouterSimulator.NUM_NODES; ++x)
                {
                    if(routes[x] == nb_y)
                    {
                        // If we are routing through y on our path to x, tell y that we 
                        // do not have a path to x
                        pk.mincost[x] = RouterSimulator.INFINITY;
                    }
                }
            }

            sendUpdate(pk);
        }
    }

    //--------------------------------------------------
    public void recvUpdate(RouterPacket pkt) {
        boolean bUpdated = false;

        // Update cost from neighbor to others in own table
        routercosts[pkt.sourceid] = pkt.mincost;

        bUpdated = recalcRoutes();

        printDistanceTable();

        if(bUpdated)
        {
            sendUpdateToNeighbors();
        }
    }

    //--------------------------------------------------
    private void sendUpdate(RouterPacket pkt) {
            if((pkt.sourceid != pkt.destid) && (costs[pkt.destid] != RouterSimulator.INFINITY))
            sim.toLayer2(pkt);
    }

    //--------------------------------------------------
    public void printDistanceTable() {
        myGUI.println("Current routercosts for " + myID +
                "  at time " + sim.getClocktime());

        int i, j;
        myGUI.println();
        myGUI.println("DistanceTable");
        myGUI.print("    dst  |");
        for(i = 0; i < RouterSimulator.NUM_NODES; i++)        
            myGUI.print("      " + i);
        
        myGUI.println();
        myGUI.println("---------------------------------");
        for(j = 0; j < RouterSimulator.NUM_NODES; j++)
        {
                myGUI.print("nbr " + F.format(j, 5) + "|");
            for(i = 0; i < RouterSimulator.NUM_NODES; i++)
            {
                    myGUI.print(F.format(routercosts[j][i], 5));
            }
            myGUI.println();
        }
        myGUI.println();
        myGUI.println("Our Distance Vector And Routes");
         myGUI.print("  dst    |");
        for(i = 0; i < RouterSimulator.NUM_NODES; i++)        
                myGUI.print(F.format(i, 5));
        
        myGUI.println();
        myGUI.println("---------------------------------");

        myGUI.print("  cost   |");
        for(i = 0; i < RouterSimulator.NUM_NODES; i++)
                myGUI.print(F.format(routercosts[myID][i], 5));
        myGUI.println();

        myGUI.print("  route  |");
        for(i = 0; i < RouterSimulator.NUM_NODES; i++)
        {
            if(routes[i] == 999)
                    myGUI.print(F.format("-", 5));
            else
                    myGUI.print(F.format(routes[i],5));
        }
        myGUI.println();
    }

    //--------------------------------------------------
    public void updateLinkCost(int dest, int newcost) {
            costs[dest] = newcost;

            boolean bUpdated = recalcRoutes();

            printDistanceTable();

            if(bUpdated)
            {
                sendUpdateToNeighbors();
            }
    }
}
