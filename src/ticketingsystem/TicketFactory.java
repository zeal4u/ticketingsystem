package ticketingsystem;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketFactory {
	private static AtomicInteger count = new AtomicInteger();

	public static Ticket ConstructTicket(String passenger, int route, int departure, int arrival, int coach, int seat){
		Ticket t = new Ticket();
		t.tid = count.getAndIncrement();
		t.passenger = passenger;
		t.route = route;
		t.coach = coach;
		t.seat = seat;
		t.departure = departure;
		t.arrival = arrival;
		return t;
	}
}
