package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.hr.contract.ContractEntity;
import com.restaurantmanager.core.hr.contract.ContractRepository;
import com.restaurantmanager.core.hr.contract.ContractStatus;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.payroll.PayrollCalculator;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estimated wage cost of a rota: each assigned shift's paid hours at the employee's hourly rate from their active
 * contract (salaries converted to an hourly figure the way payroll does). Open shifts and people without a priceable
 * contract are counted as unpriced. An estimate: salaried pay does not change with the rota, and premiums are left out.
 */
@Component
public class LabourCostCalculator {
    private final ContractRepository contractRepository;

    public LabourCostCalculator(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public record EmployeeCost(UUID employeeId, String name, long paidMinutes, BigDecimal hourlyRate, BigDecimal cost) {}

    /**
     * {@code byEmployee} is only filled when the caller may see pay figures; otherwise the total alone is given
     * (per-person cost and hours would reveal pay rates).
     */
    public record LabourCost(BigDecimal total, long pricedMinutes, int pricedShifts, int unpricedShifts, List<EmployeeCost> byEmployee) {}

    public LabourCost estimate(List<WorkScheduleEntity> shifts, Map<UUID, EmployeeEntity> employees, boolean showPeople) {
        Map<UUID, BigDecimal> rates = new HashMap<>();
        Map<UUID, long[]> minutesByEmployee = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        long pricedMinutes = 0;
        int priced = 0;
        int unpriced = 0;
        for (WorkScheduleEntity shift : shifts) {
            if (shift.getStatus() == ScheduleStatus.CANCELLED) continue;
            UUID employeeId = shift.getEmployeeId();
            BigDecimal rate = employeeId == null ? null : rates.computeIfAbsent(employeeId, this::hourlyRate);
            long minutes = ScheduleService.interval(shift).paidMinutes();
            if (rate == null || rate.signum() <= 0) {
                unpriced++;
                continue;
            }
            priced++;
            pricedMinutes += minutes;
            total = total.add(rate.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP));
            minutesByEmployee.computeIfAbsent(employeeId, k -> new long[1])[0] += minutes;
        }
        List<EmployeeCost> people = new ArrayList<>();
        if (showPeople) {
            for (Map.Entry<UUID, long[]> entry : minutesByEmployee.entrySet()) {
                BigDecimal rate = rates.get(entry.getKey());
                EmployeeEntity employee = employees.get(entry.getKey());
                people.add(new EmployeeCost(entry.getKey(), employee == null ? null : employee.fullName(), entry.getValue()[0], rate,
                        rate.multiply(BigDecimal.valueOf(entry.getValue()[0])).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)));
            }
            people.sort(Comparator.comparing(EmployeeCost::cost).reversed());
        }
        return new LabourCost(total.setScale(2, RoundingMode.HALF_UP), pricedMinutes, priced, unpriced, people);
    }

    private BigDecimal hourlyRate(UUID employeeId) {
        ContractEntity contract = contractRepository.findFirstByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE).orElse(null);
        if (contract == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = PayrollCalculator.hourlyRate(contract.getSalaryType(), contract.getSalaryAmount(), contract.getWeeklyHours());
        return rate == null ? BigDecimal.ZERO : rate;
    }
}
