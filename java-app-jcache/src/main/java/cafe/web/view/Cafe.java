package cafe.web.view;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
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

import cafe.model.entity.Coffee;

@Named
@RequestScoped
public class Cafe implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseUri;
    private transient Client client;

    @NotNull
    @NotEmpty
    protected String name;
    @NotNull
    protected Double price;
    protected Long submitCount;
    protected List<Coffee> coffeeList;

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
        this.getAllCoffees();
        return coffeeList;
    }

    public String getHostName() {
        return System.getenv("HOSTNAME");
    }

    public Long getSubmitCount() {
        return submitCount;
    }

    @PostConstruct
    private void init() {
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext()
                .getRequest();

        baseUri = "http://localhost:9080" + request.getContextPath() + "/rest/coffees";
        this.client = ClientBuilder.newBuilder().build();

        Map<String, Object> sessionMap = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
        this.name = (String) sessionMap.get("newCoffeeName");
        this.price = (Double) sessionMap.get("newCoffeePrice");
        this.submitCount =  sessionMap.containsKey("newCoffeeSubmitCount") ? (Long) sessionMap.get("newCoffeeSubmitCount") : 0;
    }

    private void getAllCoffees() {
        this.coffeeList = this.client.target(this.baseUri).path("/").request(MediaType.APPLICATION_JSON)
                .get(new GenericType<List<Coffee>>() {
                });
    }

    public void addCoffee() throws IOException {
        Map<String, Object> sessionMap = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
        sessionMap.put("newCoffeeName", this.name);
        sessionMap.put("newCoffeePrice", this.price);
        sessionMap.put("newCoffeeSubmitCount", ++this.submitCount);

        Coffee coffee = new Coffee(this.name, this.price);
        coffee.setId(this.submitCount);
        sessionMap.put("newCoffee", coffee.toString());

        FacesContext.getCurrentInstance().getExternalContext().redirect("");
    }

    public void removeCoffee(String coffeeId) throws IOException {
        this.client.target(baseUri).path(coffeeId).request().delete();
        FacesContext.getCurrentInstance().getExternalContext().redirect("");
    }
}
