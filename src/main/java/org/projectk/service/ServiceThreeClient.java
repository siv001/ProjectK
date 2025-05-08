package org.projectk.service;

import org.projectk.config.CacheConfig;
import org.projectk.dto.Employee;
import org.projectk.dto.ServiceThreeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Client for Service Three which handles employee data enrichment
 * Integrates with the dynamic cache refresh system
 */
@Service
public class ServiceThreeClient {
    private final RestTemplate restTemplate;
    private final CacheConfig cacheConfig;
    
    @Autowired
    public ServiceThreeClient(RestTemplate restTemplate, CacheConfig cacheConfig) {
        this.restTemplate = restTemplate;
        this.cacheConfig = cacheConfig;
    }
    
    /**
     * Fetches enriched employee data from the service
     * Uses the custom EmployeeKeyGenerator for cache keys
     *
     * @param employee the employee to enrich with additional data
     * @return the enriched employee response
     */
    @Cacheable(value = "serviceThreeCache", keyGenerator = "employeeKeyGenerator")
    public ServiceThreeResponse fetchEnrichedEmployee(Employee employee) {
        // Call external service to enrich employee data
        String url = "https://api.example.com/employee-service?empId=" + employee.getEmployeeId();
        
        // In a real implementation, this would call the actual service
        // Uncomment this in production and remove the mock implementation below
        // ServiceThreeResponse response = restTemplate.getForObject(url, ServiceThreeResponse.class);
        System.out.println("Would call service at: " + url);
        // Here we simulate a response with enriched employee data
        ServiceThreeResponse response = new ServiceThreeResponse();
        
        // Create a copy of the employee with enriched data
        Employee enrichedEmployee = new Employee(
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmployeeId(),
                employee.getDepartmentId()
        );
        
        // Add enriched data
        enrichedEmployee.setAddress("123 Main St, City, State 12345");
        enrichedEmployee.setBenefits("Health, Dental, Vision, 401k");
        enrichedEmployee.setPosition("Senior Developer");
        
        response.setEmployee(enrichedEmployee);
        response.setTtl(180); // TTL of 3 minutes for employee data
        
        // Track the key with its TTL for dynamic refresh scheduling
        // Important: We must use the SAME key that the cache will use
        if (response != null) {
            // Generate the same key that @Cacheable will use
            String cacheKey = "emp:" + employee.getEmployeeId();
            System.out.println("ServiceThree response for employee " + employee.getEmployeeId() + 
                             " has TTL: " + response.getTtl() + " seconds");
            cacheConfig.trackCacheKey("serviceThreeCache", cacheKey, response.getTtl());
        }
        
        return response;
    }
    
    /**
     * Method to fetch fresh data directly without caching
     * Used by the cache refresh system
     */
    public ServiceThreeResponse fetchFresh(Object key) {
        if (key instanceof String) {
            String cacheKey = (String) key;
            // Extract employee ID from the cache key
            String employeeId = cacheKey.startsWith("emp:") ? 
                    cacheKey.substring(4) : cacheKey;
            
            // Create a minimal employee with just the ID for refresh
            Employee employeeToRefresh = new Employee();
            employeeToRefresh.setEmployeeId(employeeId);
            
            // Call the real method without caching
            return fetchEmployeeDirectly(employeeToRefresh);
        }
        
        // Fallback for unexpected key type
        throw new IllegalArgumentException("Cannot refresh with key: " + key);
    }
    
    /**
     * Helper method to call the actual service without caching
     */
    private ServiceThreeResponse fetchEmployeeDirectly(Employee employee) {
        // This would be the actual service call logic without caching
        String url = "https://api.example.com/employee-service?empId=" + employee.getEmployeeId();
        
        // In production, use actual REST call
        // ServiceThreeResponse response = restTemplate.getForObject(url, ServiceThreeResponse.class);
        
        // Simulate service response
        System.out.println("Making direct service call to: " + url);
        ServiceThreeResponse response = new ServiceThreeResponse();
        
        // Create a minimal enriched employee
        Employee enrichedEmployee = new Employee();
        enrichedEmployee.setEmployeeId(employee.getEmployeeId());
        enrichedEmployee.setFirstName("Refreshed");
        enrichedEmployee.setLastName("Employee");
        enrichedEmployee.setAddress("Updated Address");
        enrichedEmployee.setBenefits("Updated Benefits");
        
        response.setEmployee(enrichedEmployee);
        response.setTtl(180);
        
        return response;
    }
}
