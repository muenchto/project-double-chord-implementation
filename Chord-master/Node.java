import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Node class that implements the core data structure 
 * and functionalities of a chord node
 * @author Chuan Xia
 *
 */

public class Node {

	private ArrayList<Long> localId;
	private InetSocketAddress localAddress;
	private InetSocketAddress predecessor;
	private HashMap<Integer, InetSocketAddress> finger;

	private Listener listener;
	private Stabilize stabilize;
	private FixFingers fix_fingers;
	private AskPredecessor ask_predecessor;
	private ServantThread sv;

	/**
	 * Constructor
	 * @param address: this node's local address
	 */
	public Node (InetSocketAddress address, final int NUM_RINGS) {

		localAddress = address;

		localId = new ArrayList<>();
        for (int i = 0; i < NUM_RINGS; i++) {
            localId.add(Helper.hashSocketAddress(localAddress, i));
        }

		// initialize an empty finge table
		finger = new HashMap<Integer, InetSocketAddress>();
		for (int i = 1; i <= 32; i++) {
			updateIthFinger (i, null);
		}

		// initialize predecessor
		predecessor = null;

		// initialize threads
		listener = new Listener(this);
		stabilize = new Stabilize(this);
		fix_fingers = new FixFingers(this);
		ask_predecessor = new AskPredecessor(this);
		sv = new ServantThread(this);
	}

	/**
	 * Create or join a ring 
	 * @param contact
	 * @return true if successfully create a ring
	 * or join a ring via contact
	 */
	public boolean join (InetSocketAddress contact) {

		// if contact is other node (join ring), try to contact that node
		// (contact will never be null)
		if (contact != null && !contact.equals(localAddress)) {
			InetSocketAddress successor = Helper.requestAddress(contact, "FINDSUCC_" + localId.get(0));
			if (successor == null)  {
				System.out.println("\nCannot find node you are trying to contact. Please exit.\n");
				return false;
			}
			updateIthFinger(1, successor);
		}

		// start all threads	
		listener.start();
		stabilize.start();
		fix_fingers.start();
		ask_predecessor.start();
		sv.start();

		return true;
	}

	/**
	 * Notify successor that this node should be its predecessor
	 * @param successor
	 * @return successor's response
	 */
	public String notify(InetSocketAddress successor) {
		if (successor!=null && !successor.equals(localAddress))
			return Helper.sendRequest(successor, "IAMPRE_"+localAddress.getAddress().toString()+":"+localAddress.getPort());
		else
			return null;
	}

	/**
	 * Being notified by another node, set it as my predecessor if it is.
	 * @param newpre
	 */
	public void notified (InetSocketAddress newpre) {
		if (predecessor == null || predecessor.equals(localAddress)) {
			this.setPredecessor(newpre);
		}
		else {
			long oldpre_id = Helper.hashSocketAddress(predecessor, 0);
			long local_relative_id = Helper.computeRelativeId(localId.get(0), oldpre_id);
			long newpre_relative_id = Helper.computeRelativeId(Helper.hashSocketAddress(newpre, 0), oldpre_id);
			if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id)
				this.setPredecessor(newpre);
		}
	}

	/**
	 * Ask current node to find id's successor.
	 * @param id
	 * @return id's successor's socket address
	 */
	public InetSocketAddress find_successor (long id, int ring_nr) {

		// initialize return value as this node's successor (might be null)
		InetSocketAddress ret = this.getSuccessor();

		// find predecessor
		InetSocketAddress pre = find_predecessor(id, ring_nr);

		// if other node found, ask it for its successor
		if (!pre.equals(localAddress))
			ret = Helper.requestAddress(pre, "YOURSUCC");

		// if ret is still null, set it as local node, return
		if (ret == null)
			ret = localAddress;

		return ret;
	}

	/**
	 * Ask current node to find id's predecessor
	 * @param findid
	 * @return id's successor's socket address
	 */
	private InetSocketAddress find_predecessor (long findid, int ring_nr) {
		InetSocketAddress n = this.localAddress;
		InetSocketAddress n_successor = this.getSuccessor();
		InetSocketAddress most_recently_alive = this.localAddress;
        long findid_relative_id;

        //dont look in a specific ring
        if (ring_nr == -1) {

            //check in which ring the findid is closer to local node
            long findid_relative_id0 = Helper.computeRelativeId(findid, Helper.hashSocketAddress(n, 0));
            long findid_relative_id1 = Helper.computeRelativeId(findid, Helper.hashSocketAddress(n, 1));

            if (findid_relative_id0 <= findid_relative_id1) {
                findid_relative_id = findid_relative_id0;
                ring_nr = 0;
            } else {
                findid_relative_id = findid_relative_id1;
                ring_nr = 1;
            }
        }
        else {
            findid_relative_id = Helper.computeRelativeId(findid, Helper.hashSocketAddress(n, ring_nr));
        }

		long n_successor_relative_id = 0;
		if (n_successor != null)
			n_successor_relative_id = Helper.computeRelativeId(Helper.hashSocketAddress(n_successor, ring_nr), Helper.hashSocketAddress(n, ring_nr));



		while (!(findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id)) {

			// temporarily save current node
			InetSocketAddress pre_n = n;

			// if current node is local node, find my closest
			if (n.equals(this.localAddress)) {
				n = this.closest_preceding_finger(findid, ring_nr);
			}

			// else current node is remote node, sent request to it for its closest
			else {
				InetSocketAddress result = Helper.requestAddress(n, "CLOSEST_" + findid, ring_nr);

				// if fail to get response, set n to most recently 
				if (result == null) {
					n = most_recently_alive;
					n_successor = Helper.requestAddress(n, "YOURSUCC", ring_nr);
					if (n_successor==null) {
						System.out.println("It's not possible.");
						return localAddress;
					}
					continue;
				}

				// if n's closest is itself, return n
				else if (result.equals(n))
					return result;

				// else n's closest is other node "result"
				else {	
					// set n as most recently alive
					most_recently_alive = n;		
					// ask "result" for its successor
					n_successor = Helper.requestAddress(result, "YOURSUCC", ring_nr);
					// if we can get its response, then "result" must be our next n
					if (n_successor!=null) {
						n = result;
					}
					// else n sticks, ask n's successor
					else {
						n_successor = Helper.requestAddress(n, "YOURSUCC", ring_nr);
					}
				}

				// compute relative ids for while loop judgement
				n_successor_relative_id = Helper.computeRelativeId(Helper.hashSocketAddress(n_successor, ring_nr), Helper.hashSocketAddress(n, ring_nr));
				findid_relative_id = Helper.computeRelativeId(findid, Helper.hashSocketAddress(n, ring_nr));
			}
			if (pre_n.equals(n))
				break;
		}
		return n;
	}

	/**
	 * Return closest finger preceding node.
	 * @param findid
	 * @return closest finger preceding node's socket address
	 */
	public InetSocketAddress closest_preceding_finger (long findid, int ring_nr) {

	    //ring number should not be -1, but if it is, FATAL ERROR, exit!
        if (ring_nr == -1) {
            System.out.println("FATAL ERROR: ring number was -1 in closest_preceding_finger()!");
            System.exit(1);
        }

		long findid_relative = Helper.computeRelativeId(findid, localId.get(ring_nr));

		// check from last item in finger table
		for (int i = 32; i > 0; i--) {
			InetSocketAddress ith_finger = finger.get(i);
			if (ith_finger == null) {
				continue;
			}
			long ith_finger_id = Helper.hashSocketAddress(ith_finger, ring_nr);
			long ith_finger_relative_id = Helper.computeRelativeId(ith_finger_id, localId.get(ring_nr));

			// if its relative id is the closest, check if its alive
			if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative)  {
				String response  = Helper.sendRequest(ith_finger, "KEEP");

				//it is alive, return it
				if (response!=null &&  response.equals("ALIVE")) {
					return ith_finger;
				}

				// else, remove its existence from finger table
				else {
					updateFingers(-2, ith_finger);
				}
			}
		}
		return localAddress;
	}

	/**
	 * Update the finger table based on parameters.
	 * Synchronize, all threads trying to modify 
	 * finger table only through this method. 
	 * @param i: index or command code
	 * @param value
	 */
	public synchronized void updateFingers(int i, InetSocketAddress value) {

		// valid index in [1, 32], just update the ith finger
		if (i > 0 && i <= 32) {
			updateIthFinger(i, value);
		}

		// caller wants to delete
		else if (i == -1) {
			deleteSuccessor();
		}

		// caller wants to delete a finger in table
		else if (i == -2) {
			deleteCertainFinger(value);

		}

		// caller wants to fill successor
		else if (i == -3) {
			fillSuccessor();
		}

	}

	/**
	 * Update ith finger in finger table using new value
	 * @param i: index
	 * @param value
	 */
	private void updateIthFinger(int i, InetSocketAddress value) {
		finger.put(i, value);

		// if the updated one is successor, notify the new successor
		if (i == 1 && value != null && !value.equals(localAddress)) {
			notify(value);
		}
	}

	/**
	 * Delete successor and all following fingers equal to successor
	 */
	private void deleteSuccessor() {
		InetSocketAddress successor = getSuccessor();

		//nothing to delete, just return
		if (successor == null)
			return;

		// find the last existence of successor in the finger table
		int i = 32;
		for (i = 32; i > 0; i--) {
			InetSocketAddress ithfinger = finger.get(i);
			if (ithfinger != null && ithfinger.equals(successor))
				break;
		}

		// delete it, from the last existence to the first one
		for (int j = i; j >= 1 ; j--) {
			updateIthFinger(j, null);
		}

		// if predecessor is successor, delete it
		if (predecessor!= null && predecessor.equals(successor))
			setPredecessor(null);

		// try to fill successor
		fillSuccessor();
		successor = getSuccessor();

		// if successor is still null or local node, 
		// and the predecessor is another node, keep asking 
		// it's predecessor until find local node's new successor
		if ((successor == null || successor.equals(successor)) && predecessor!=null && !predecessor.equals(localAddress)) {
			InetSocketAddress p = predecessor;
			InetSocketAddress p_pre = null;
			while (true) {
				p_pre = Helper.requestAddress(p, "YOURPRE");
				if (p_pre == null)
					break;

				// if p's predecessor is node is just deleted, 
				// or itself (nothing found in p), or local address,
				// p is current node's new successor, break
				if (p_pre.equals(p) || p_pre.equals(localAddress)|| p_pre.equals(successor)) {
					break;
				}

				// else, keep asking
				else {
					p = p_pre;
				}
			}

			// update successor
			updateIthFinger(1, p);
		}
	}

	/**
	 * Delete a node from the finger table, here "delete" means deleting all existence of this node 
	 * @param f
	 */
	private void deleteCertainFinger(InetSocketAddress f) {
		for (int i = 32; i > 0; i--) {
			InetSocketAddress ithfinger = finger.get(i);
			if (ithfinger != null && ithfinger.equals(f))
				finger.put(i, null);
		}
	}

	/**
	 * Try to fill successor with candidates in finger table or even predecessor
	 */
	private void fillSuccessor() {
		InetSocketAddress successor = this.getSuccessor();
		if (successor == null || successor.equals(localAddress)) {
			for (int i = 2; i <= 32; i++) {
				InetSocketAddress ithfinger = finger.get(i);
				if (ithfinger!=null && !ithfinger.equals(localAddress)) {
					for (int j = i-1; j >=1; j--) {
						updateIthFinger(j, ithfinger);
					}
					break;
				}
			}
		}
		successor = getSuccessor();
		if ((successor == null || successor.equals(localAddress)) && predecessor!=null && !predecessor.equals(localAddress)) {
			updateIthFinger(1, predecessor);
		}

	}


	/**
	 * Clear predecessor.
	 */
	public void clearPredecessor () {
		setPredecessor(null);
	}

	/**
	 * Set predecessor using a new value.
	 * @param pre
	 */
	private synchronized void setPredecessor(InetSocketAddress pre) {
		predecessor = pre;
	}


	/**
	 * Getters
	 * @return the variable caller wants
	 */

	public long getId() {
		return localId.get(0);
	}

	public InetSocketAddress getAddress() {
		return localAddress;
	}

	public InetSocketAddress getPredecessor() {
		return predecessor;
	}

	public InetSocketAddress getSuccessor() {
		if (finger != null && finger.size() > 0) {
			return finger.get(1);
		}
		return null;
	}

	/**
	 * Print functions
	 */

	public void printNeighbors () {
		System.out.println("\nYou are listening on port "+localAddress.getPort()+"."
				+ "\nYour position is "+Helper.hexIdAndPosition(localAddress)+".");
		InetSocketAddress successor = finger.get(1);
		
		// if it cannot find both predecessor and successor
		if ((predecessor == null || predecessor.equals(localAddress)) && (successor == null || successor.equals(localAddress))) {
			System.out.println("Your predecessor is yourself.");
			System.out.println("Your successor is yourself.");

		}
		
		// else, it can find either predecessor or successor
		else {
			if (predecessor != null) {
				System.out.println("Your predecessor is node "+predecessor.getAddress().toString()+", "
						+ "port "+predecessor.getPort()+ ", position "+Helper.hexIdAndPosition(predecessor)+".");
			}
			else {
				System.out.println("Your predecessor is updating.");
			}

			if (successor != null) {
				System.out.println("Your successor is node "+successor.getAddress().toString()+", "
						+ "port "+successor.getPort()+ ", position "+Helper.hexIdAndPosition(successor)+".");
			}
			else {
				System.out.println("Your successor is updating.");
			}
		}
	}

	public void printDataStructure () {
		System.out.println("\n==============================================================");
		System.out.println("\nLOCAL:\t\t\t\t"+localAddress.toString()+"\t"+Helper.hexIdAndPosition(localAddress));
		if (predecessor != null)
			System.out.println("\nPREDECESSOR:\t\t\t"+predecessor.toString()+"\t"+Helper.hexIdAndPosition(predecessor));
		else 
			System.out.println("\nPREDECESSOR:\t\t\tNULL");
		System.out.println("\nFINGER TABLE:\n");
		for (int i = 1; i <= 32; i++) {
			long ithstart = Helper.ithStart(Helper.hashSocketAddress(localAddress, 0),i);
			InetSocketAddress f = finger.get(i);
			StringBuilder sb = new StringBuilder();
			sb.append(i+"\t"+ Helper.longTo8DigitHex(ithstart)+"\t\t");
			if (f!= null)
				sb.append(f.toString()+"\t"+Helper.hexIdAndPosition(f));

			else 
				sb.append("NULL");
			System.out.println(sb.toString());
		}
		System.out.println("\n==============================================================\n");
	}

	/**
	 * Stop this node's all threads.
	 */
	public void stopAllThreads() {
		if (listener != null)
			listener.toDie();
		if (fix_fingers != null)
			fix_fingers.toDie();
		if (stabilize != null)
			stabilize.toDie();
		if (ask_predecessor != null)
			ask_predecessor.toDie();
	}

	private class ServantThread extends Thread{

		private Map<String,String> ips;
		private Map<String,String> domain;
		private ServerSocket sv;
		private boolean alive;

		ServantThread(Node n){

			ips = new HashMap<>();
			domain = new HashMap<>();
			alive = true;
			InetSocketAddress localAddress = n.getAddress();
			int port = localAddress.getPort() + 2000;
			System.out.println("port " + port);
			try {
				sv = new ServerSocket(port);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void run() {
			while(alive) {
				try {
					Socket ss = sv.accept();
					ResponseThread rt = new ResponseThread(ss, ips, domain);
					rt.start();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		public void toDie() {
			alive = false;
		}

		private class ResponseThread extends Thread {

			private Socket ss;
			private Map<String,String> ips;
			private Map<String,String> domain;

			ResponseThread(Socket ss, Map<String,String> ips, Map<String,String> domain){
				this.ss = ss;
				this.ips = ips;
				this.domain = domain;
			}

			public void run() {

				try {
					ObjectOutputStream oos = new ObjectOutputStream(ss.getOutputStream());
					ObjectInputStream ois = new ObjectInputStream(ss.getInputStream());
					oos.flush();
					String type = (String) ois.readObject();

					if(type.equals("putd")) {
						String dom = (String) ois.readObject();
						String ip = (String) ois.readObject();

						System.out.println("put Domain -> IP: " + dom + " -> "+ ip );
						domain.put(dom, ip);
					}else if(type.equals("putip")) {

						String dom = (String) ois.readObject();
						String ip = (String) ois.readObject();

						System.out.println("put IP -> Domain: " + ip + " -> "+ dom );

						ips.put(ip, dom);


					}else if(type.equals("getd")) {
						String dom = (String) ois.readObject();
						String ret = domain.get(dom);

						System.out.println("The IP of Domain: "+ dom + " is: "+ret);
						oos.writeObject(ret);
						oos.flush();
					}else if(type.equals("getip")) {
						String ip = (String) ois.readObject();
						String ret = ips.get(ip);

						System.out.println("The Domain of IP: " + ip + " is: " + ret);
						oos.writeObject(ret);
						oos.flush();
					}

					ois.close();
					oos.close();
					ss.close();

				} catch (IOException | ClassNotFoundException e) {

					e.printStackTrace();
				}
			}
		}
	}
}

