package com.restaurantmanager.core.branch;

import com.restaurantmanager.core.branch.dto.BranchRequest;
import com.restaurantmanager.core.branch.dto.BranchResponse;
import com.restaurantmanager.core.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BranchService {
    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        return branchRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BranchResponse create(BranchRequest request) {
        if (branchRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw new ApiException(409, "Branch code already exists");
        }
        BranchEntity branch = new BranchEntity();
        applyRequest(branch, request);
        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse update(UUID id, BranchRequest request) {
        BranchEntity branch = branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Branch not found"));
        String incomingCode = request.code().trim();
        if (!branch.getCode().equalsIgnoreCase(incomingCode) && branchRepository.existsByCodeIgnoreCase(incomingCode)) {
            throw new ApiException(409, "Branch code already exists");
        }
        applyRequest(branch, request);
        return toResponse(branchRepository.save(branch));
    }

    public BranchEntity require(UUID branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ApiException(404, "Branch not found"));
    }

    private void applyRequest(BranchEntity branch, BranchRequest request) {
        branch.setCode(request.code().trim());
        branch.setName(request.name().trim());
        branch.setActive(request.active());
    }

    private BranchResponse toResponse(BranchEntity branch) {
        return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.isActive());
    }
}
