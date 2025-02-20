package cafe.web.view;

import cafe.model.entity.Coffee;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;

import java.lang.invoke.MethodHandles;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@SessionScoped
public class Cafe implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    private transient String baseUri;
    private transient Client client;

    @NotNull
    @NotEmpty
    protected String name;
    @NotNull
    protected Double price;

    protected transient List<Coffee> coffeeList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public List<Coffee> getCoffeeList() {
        return coffeeList;
    }

    public String getHostName() {
        return System.getenv("HOSTNAME");
    }

    @PostConstruct
    private void init() {
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext()
                .getRequest();
        baseUri = "http://localhost:9080" + request.getContextPath() + "/rest/coffees";
        client = ClientBuilder.newBuilder().build();
        getAllCoffees();
    }

    private void getAllCoffees() {
        coffeeList = client.target(baseUri).path("/").request(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<Coffee>>() {
                });
    }

    public void addCoffee() throws IOException {
        Coffee coffee = new Coffee(name, price);
        client.target(baseUri).request(MediaType.APPLICATION_JSON).post(Entity.json(coffee));
        getAllCoffees();
        FacesContext.getCurrentInstance().getExternalContext().redirect("");
    }

    public void removeCoffee(String coffeeId) throws IOException {
        client.target(baseUri).path(coffeeId).request().delete();
        getAllCoffees();
        FacesContext.getCurrentInstance().getExternalContext().redirect("");
    }

    // This method is called after deserialization to initialize transient fields.
    private Object readResolve() {
        init();
        return this;
    }
}
