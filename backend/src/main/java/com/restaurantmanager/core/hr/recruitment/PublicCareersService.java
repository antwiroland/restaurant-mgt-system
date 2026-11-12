package com.restaurantmanager.core.hr.recruitment;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.hr.employee.EmploymentType;
import com.restaurantmanager.core.hr.org.PositionEntity;
import com.restaurantmanager.core.hr.org.PositionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The public careers page: vacancies HR has published that are open and still accepting applicants, with only what a
 * job seeker should see. Unpublishing, or any status other than open, takes a job off the page.
 * Pay ranges, hiring managers, applicant numbers and internal notes are never exposed here.
 */
@Service
public class PublicCareersService {
    private final VacancyRepository vacancyRepository;
    private final BranchRepository branchRepository;
    private final PositionRepository positionRepository;
    private final ZoneId zone;

    public PublicCareersService(VacancyRepository vacancyRepository, BranchRepository branchRepository, PositionRepository positionRepository,
                                @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.vacancyRepository = vacancyRepository;
        this.branchRepository = branchRepository;
        this.positionRepository = positionRepository;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public List<PublicVacancy> list() {
        LocalDate today = LocalDate.now(zone);
        List<VacancyEntity> open = vacancyRepository.findByStatusAndPublishedTrueOrderByPublishedAtDesc(VacancyStatus.OPEN).stream()
                .filter(v -> isListed(v, today))
                .toList();
        Map<UUID, BranchEntity> branches = branchRepository.findAllById(open.stream().map(VacancyEntity::getBranchId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BranchEntity::getId, Function.identity()));
        Map<UUID, PositionEntity> positions = positionRepository.findAllById(open.stream().map(VacancyEntity::getPositionId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PositionEntity::getId, Function.identity()));
        return open.stream()
                .filter(v -> branches.containsKey(v.getBranchId()) && branches.get(v.getBranchId()).isActive())
                .map(v -> view(v, branches.get(v.getBranchId()), v.getPositionId() == null ? null : positions.get(v.getPositionId())))
                .sorted(Comparator.comparing(PublicVacancy::postedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicVacancy get(UUID id) {
        VacancyEntity v = vacancyRepository.findById(id)
                .filter(x -> isListed(x, LocalDate.now(zone)))
                .orElseThrow(() -> new ApiException(404, "This job is no longer open"));
        BranchEntity branch = branchRepository.findById(v.getBranchId()).filter(BranchEntity::isActive)
                .orElseThrow(() -> new ApiException(404, "This job is no longer open"));
        PositionEntity position = v.getPositionId() == null ? null : positionRepository.findById(v.getPositionId()).orElse(null);
        return view(v, branch, position);
    }

    /** Published by HR, open, not past its closing date, and with places left. */
    static boolean isListed(VacancyEntity v, LocalDate today) {
        return v.isPublished() && v.getStatus() == VacancyStatus.OPEN
                && (v.getClosingDate() == null || !v.getClosingDate().isBefore(today))
                && v.getHiredCount() < v.getOpenings();
    }

    private static PublicVacancy view(VacancyEntity v, BranchEntity branch, PositionEntity position) {
        // a vacancy may leave the text blank and rely on the position's standard description and requirements
        String description = firstText(v.getDescription(), position == null ? null : position.getDescription());
        String requirements = firstText(v.getRequirements(), position == null ? null : position.getRequirements());
        String department = position == null || position.getDepartment() == null ? null : position.getDepartment().getName();
        return new PublicVacancy(v.getId(), v.getVacancyNumber(), v.getTitle(), branch.getName(), department, v.getEmploymentType(),
                v.getOpenings() - v.getHiredCount(), v.getClosingDate(), v.getPublishedAt(), description, requirements);
    }

    private static String firstText(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred.trim();
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    public record PublicVacancy(UUID id, String reference, String title, String branchName, String department, EmploymentType employmentType,
                                int openings, LocalDate closingDate, Instant postedAt, String description, String requirements) {}
}
