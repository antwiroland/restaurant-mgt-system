package com.restaurantmanager.core.media;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class MenuImageService {
    private final MenuItemRepository menuItemRepository;
    private final MediaStorage storage;
    private final MediaProps props;

    public MenuImageService(MenuItemRepository menuItemRepository, MediaStorage storage, MediaProps props) {
        this.menuItemRepository = menuItemRepository;
        this.storage = storage;
        this.props = props;
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_CATEGORIES, CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public ImageResponse upload(UUID menuItemId, MultipartFile file, String baseUrl) {
        MenuItemEntity item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "Choose an image to upload");
        }
        if (file.getSize() > props.getMaxBytes()) {
            throw new ApiException(400, "Image must be " + (props.getMaxBytes() / (1024 * 1024)) + " MB or smaller");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(400, "Could not read the uploaded file");
        }
        ImageType type = detect(content);
        if (type == null) {
            throw new ApiException(400, "Only JPEG, PNG or WebP images are accepted");
        }
        MediaStorage.StoredMedia stored = storage.store(content, type.contentType, type.extension, baseUrl);
        String previous = item.getImagePublicId();
        item.setImageUrl(stored.url());
        item.setImagePublicId(stored.publicId());
        menuItemRepository.save(item);
        if (previous != null) {
            storage.delete(previous);
        }
        return new ImageResponse(item.getId(), stored.url());
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_CATEGORIES, CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public void remove(UUID menuItemId) {
        MenuItemEntity item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        String previous = item.getImagePublicId();
        item.setImageUrl(null);
        item.setImagePublicId(null);
        menuItemRepository.save(item);
        if (previous != null) {
            storage.delete(previous);
        }
    }

    /** Identifies the image by its file signature rather than trusting the declared content type. */
    static ImageType detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return ImageType.PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }

    enum ImageType {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    public record ImageResponse(UUID menuItemId, String imageUrl) {}
}
