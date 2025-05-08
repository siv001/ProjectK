package org.projectk.config;

import org.projectk.dto.Employee;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Custom key generator for Employee objects
 * This ensures consistent key generation for cache operations
 */
@Component
public class EmployeeKeyGenerator implements KeyGenerator {
    
    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length == 0) {
            return "EMPTY_KEY";
        }
        
        // Extract Employee object from parameters
        if (params[0] instanceof Employee) {
            Employee employee = (Employee) params[0];
            // Use employeeId as the primary key identifier
            // Fallback to name+dept if id is not available
            if (employee.getEmployeeId() != null && !employee.getEmployeeId().isEmpty()) {
                return "emp:" + employee.getEmployeeId();
            } else {
                return "emp:" + employee.getFirstName() + "-" + 
                       employee.getLastName() + "-" + 
                       employee.getDepartmentId();
            }
        }
        
        // If not an Employee, use standard toString
        return params[0] != null ? params[0].toString() : "null";
    }
}
