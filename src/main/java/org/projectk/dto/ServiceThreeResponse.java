package org.projectk.dto;

public class ServiceThreeResponse {
    private Employee employee;
    private int ttl;
    
    public ServiceThreeResponse() {
    }
    
    public Employee getEmployee() {
        return employee;
    }
    
    public void setEmployee(Employee employee) {
        this.employee = employee;
    }
    
    public int getTtl() {
        return ttl;
    }
    
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
