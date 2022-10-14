package cafe.web.rest;

import cafe.model.CafeRepository;
import cafe.model.entity.Coffee;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("coffees")
public class CafeResource {

	@Inject
	private CafeRepository cafeRepository;

	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public List<Coffee> getAllCoffees() {
		return this.cafeRepository.getAllCoffees();
	}

	@POST
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })	
	public Coffee createCoffee(Coffee coffee) {
		return this.cafeRepository.persistCoffee(coffee);
	}

	@GET
	@Path("{id}")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public Coffee getCoffeeById(@PathParam("id") Long coffeeId) {
		return this.cafeRepository.findCoffeeById(coffeeId);
	}

	@DELETE
	@Path("{id}")
	public void deleteCoffee(@PathParam("id") Long coffeeId) {
		this.cafeRepository.removeCoffeeById(coffeeId);
	}
}