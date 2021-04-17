/* 
 * Copyright 2010 Aalto University, ComNet
 *Edited by Sibusiso Shabalala (to make it Energy Efficient Routing Protocol) 
 * Released under GPLv3. See LICENSE.txt for details. j
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static routing.MessageRouter.RCV_OK;
import routing.util.EnergyModel;
import util.Tuple;

/**
* E-Epidemic message router with drop-oldest buffer and only single transferring
* connections at a time.
*/

public class E_EpidemicRouter extends ActiveRouter {
	
	public Map<String, Integer> delivered;//Hash Table to store msg ID, source ID and destination ID
	private static double threshold;
	static double battery_level_threshold; // Minimum battery power
	private static int transFactor; //transmission factor
	
	static {
		Settings s = new Settings();
		threshold = s.getDouble("E_EpidemicRouter.threshold");
		battery_level_threshold = s.getInt("E_EpidemicRouter.transFactor");
		transFactor = s.getInt("E_EpidemicRouter.transmissionFactor");
		}
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public E_EpidemicRouter(Settings s) {
		super(s);
		//TODO: read&use e_epidemic router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected E_EpidemicRouter(E_EpidemicRouter r) {
		super(r);
		initDelivered();
		//TODO: copy epidemic settings here (if any)
	}
	
	/**
	* Initializes Delivered hash
	*/
	private void initDelivered() {
		this.delivered = new HashMap<String, Integer>(200);
		}
			
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		// then try any/all message to any/all connection
		//this.tryAllMessagesToAllConnections();
	}
	
	/**
	* Tries to send all other messages to all connected hosts ordered by their
	* delivery probability
	*
	* @return The return value of {@link #tryMessagesForConnected(List)}
	*/
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
		Collection<Message> msgCollection = getMessageCollection();
		Collection<Message> msg_to_be_deleted = new HashSet<Message>();
		
		/* for all connected hosts collect all messages that have a higher
		probability of delivery by the other host */
		
		for (Connection con : getConnections()) { // DTNHost me = getHost();//requesting to get connection
			DTNHost other = con.getOtherNode(getHost());
			E_EpidemicRouter othRouter = (E_EpidemicRouter) other.getRouter();
			
			 if (othRouter.isTransferring()) {
			continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
			if (othRouter.hasMessage(m.getId())) {
			continue; // skip messages that the other one has
			}   
			
			double curr_energy = (double) othRouter.getHost().getComBus().getProperty(EnergyModel.INIT_ENERGY_S);
			DTNHost dest = m.getTo(); //creat the object of the destination of the msg to contain destination ID
			String key = m.getId() + "<->" + m.getFrom().toString() + "<->" + dest.toString(); //we are getting the msg ID, Source ID and the destinatio ID od the current msg (Ack_table)
			System.out.println(curr_energy);
			if (othRouter.delivered.containsKey(key)) {
			int cnt = (int) othRouter.delivered.get(key);
			this.delivered.put(key, ++cnt);///update Ack_Table
			msg_to_be_deleted.add(m);//delate the msg
			continue;
			}
			// if energy of neighbour host is less than the battery level threshold and neighbour host is not the destination node
			if (curr_energy < E_EpidemicRouter.battery_level_threshold && !dest.equals(other)) {
			continue;
			}
			//if other node is destination node then Increase computional performance and save energy by skipping metric computions
			if (dest.equals(other)) {
			// the other node has higher probability of delivery
			messages.add(new Tuple<Message, Connection>(m, con));
			this.delivered.put(key, 1);
			}
			}
			}
			if (messages.size() == 0) {
			return null;
			}
			if (msg_to_be_deleted.size() > 0) {
			for (Message m : msg_to_be_deleted) {
			this.deleteMessage(m.getId(), false);
			}
			}
			return tryMessagesForConnected(messages);
			}
	//==================Recieve the msg============================
	@Override
	public int receiveMessage(Message m, DTNHost from) {
	if (m.getSize() == -1) {///-1 represent ack_M if the msg size is -1, there if it ack_M, do the following
	String ack_m = m.getId();//we create the object to contan msg ID
	this.delivered.put(ack_m, 1);// we put the msd ID to ack_table
	String[] parts = ack_m.split("<->");//
	String m_Id = parts[0];
	this.deleteMessage(m_Id, false);// Delete with that ID
	return 0;
	}
	int i = super.receiveMessage(m, from);
	if (m.getTo().equals(this.getHost()) && i == RCV_OK) {
	String ack_m = m.getId() + "<->" + m.getFrom().toString() + "<->" + m.getTo().toString();//delear new ack_m to be sent to the last sender node
	Message ack_mes = new Message(this.getHost(), from, ack_m, -1);//creating ack_m with the size -1
	from.receiveMessage(ack_mes, this.getHost());//get the host for the last sender
	this.delivered.put(ack_m, 1);// send the ack_m to the last sender
	}
	return i;
	}
	@Override
	public E_EpidemicRouter replicate() {
		return new E_EpidemicRouter(this);
	}

}
