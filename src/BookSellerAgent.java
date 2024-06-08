package jadelab2;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class BookSellerAgent extends Agent {
	private Hashtable<String, Integer> catalogue; // TODO: Katalog książek dostępnych do sprzedaży (tytuł -> cena)
	private Hashtable<String, String> reservations; // TODO: Rezerwacje książek (tytuł -> kupujący)
	private BookSellerGui myGui;

	protected void setup() {
		catalogue = new Hashtable<>();
		reservations = new Hashtable<>();
		myGui = new BookSellerGui(this);
		myGui.display();

		//book selling service registration at DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		addBehaviour(new OfferRequestsServer()); // TODO: Dodanie zachowania obsługującego żądania ofert
		addBehaviour(new PurchaseOrdersServer()); // TODO: Dodanie zachowania obsługującego zamówienia
	}

	protected void takeDown() {
		//book selling service deregistration at DF
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		myGui.dispose();
		System.out.println("Seller agent " + getAID().getName() + " terminated.");
	}

	//invoked from GUI, when a new book is added to the catalogue
	public void updateCatalogue(final String title, final int price) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				catalogue.put(title, price);
				System.out.println(getAID().getLocalName() + ": " + title + " put into the catalogue. Price = " + price);
			}
		});
	}

	// TODO: Zachowanie obsługujące żądania ofert
	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			//proposals only template
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				synchronized (catalogue) { // TODO: Synchronizacja dostępu do katalogu
					Integer price = catalogue.get(title);
					if (price != null && !reservations.containsKey(title)) {
						//title found in the catalogue and not reserved, respond with its price as a proposal
						reply.setPerformative(ACLMessage.PROPOSE);
						reply.setContent(String.valueOf(price));
						reservations.put(title, msg.getSender().getLocalName()); // TODO: Rezerwacja książki
						System.out.println(getAID().getLocalName() + ": Book " + title + " reserved for " + msg.getSender().getLocalName());
					} else {
						//title not found in the catalogue or reserved
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("not-available");
						System.out.println(getAID().getLocalName() + ": Book " + title + " is not available.");
					}
				}

				myAgent.send(reply);
			} else {
				block();
			}
		}
	}

	// TODO: Zachowanie obsługujące zamówienia
	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			//purchase order as proposal acceptance only template
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				synchronized (catalogue) { // TODO: Synchronizacja dostępu do katalogu
					String reservedBy = reservations.get(title);
					if (reservedBy != null && reservedBy.equals(msg.getSender().getLocalName())) {
						Integer price = catalogue.remove(title); // TODO: Usunięcie książki z katalogu
						reservations.remove(title); // TODO: Usunięcie rezerwacji
						if (price != null) {
							reply.setPerformative(ACLMessage.INFORM);
							System.out.println(getAID().getLocalName() + ": " + title + " sold to " + msg.getSender().getLocalName());
						} else {
							//title not found in the catalogue, sold to another agent in the meantime (after proposal submission)
							reply.setPerformative(ACLMessage.FAILURE);
							reply.setContent("not-available");
							System.out.println(getAID().getLocalName() + ": Book " + title + " was already sold to another agent.");
						}
					} else {
						reply.setPerformative(ACLMessage.FAILURE);
						reply.setContent("not-available");
						System.out.println(getAID().getLocalName() + ": Book " + title + " is not reserved for " + msg.getSender().getLocalName());
					}
				}

				myAgent.send(reply);
			} else {
				block();
			}
		}
	}
}
