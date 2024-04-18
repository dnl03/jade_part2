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
  private Hashtable catalogue;
  private BookSellerGui myGui;

  protected void setup() {
    catalogue = new Hashtable();
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
    
    addBehaviour(new OfferRequestsServer());

    addBehaviour(new PurchaseOrdersServer());
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
	//TODO: #2 Dodanie Shipping Cost jako argumentu + Zmiana Obiektu na BookInfo
  public void updateCatalogue(final String title, final int price, final int shippingCost) {
    addBehaviour(new OneShotBehaviour() {
      public void action() {
		catalogue.put(title, new BookInfo(price, shippingCost));
		//TODO: #2 Dodanie informacji o cenie przesyłki
		System.out.println(getAID().getLocalName() + ": " + title + " put into the catalogue. Price = " + price + ". Shipping Cost = " + shippingCost + ". All = " + price + shippingCost);
      }
    } );
  }
  
	private class OfferRequestsServer extends CyclicBehaviour {
	  public void action() {
	    //proposals only template
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null) {
	      String title = msg.getContent();
	      ACLMessage reply = msg.createReply();
	      //TODO #4 Pobranie informacji o książce z katalogu
		  BookInfo bookInfo = (BookInfo) catalogue.get(title);
	      if (bookInfo != null) {
	        //title found in the catalogue, respond with its price as a proposal
	        reply.setPerformative(ACLMessage.PROPOSE);
	        reply.setContent(String.valueOf(bookInfo.totalCost)); //TODO #4: ustawienie ceny+ceny dostawy książki jako kontent
	      }
	      else {
	        //title not found in the catalogue
	        reply.setPerformative(ACLMessage.REFUSE);
	        reply.setContent("not-available");
	      }
	      myAgent.send(reply);
	    }
	    else {
	      block();
	    }
	  }
	}

	
	private class PurchaseOrdersServer extends CyclicBehaviour {
	  public void action() {
	    //purchase order as proposal acceptance only template
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null) {
	      String title = msg.getContent();
	      ACLMessage reply = msg.createReply();
		  //TODO:  #5 wysłanie komunikatu ze ksiażka została sprzedana
		  BookInfo bookInfo = (BookInfo) catalogue.get(title);
	      if (bookInfo != null) {
	    	  int totalPrice = bookInfo.totalCost;
	          reply.setPerformative(ACLMessage.INFORM);
	          System.out.println(getAID().getLocalName() + ": " + title + " sold to " + msg.getSender().getLocalName() + " for: " + String.valueOf(totalPrice));
	      }
	      else {
	        //title not found in the catalogue, sold to another agent in the meantime (after proposal submission)
	        reply.setPerformative(ACLMessage.FAILURE);
	        reply.setContent("not-available");
	      }
	      myAgent.send(reply);
	    }
	    else {
		  block();
		}
	  }
	}
}
//TODO: #1 Klasa przechowująca informacje o książce
class BookInfo {
	int priceBook;
	int shippingCost;
	int totalCost;
	public BookInfo(int priceBook, int shippingCost){
		this.priceBook = priceBook;
		this.shippingCost = shippingCost;
		this.totalCost = priceBook + shippingCost;
	}
}
