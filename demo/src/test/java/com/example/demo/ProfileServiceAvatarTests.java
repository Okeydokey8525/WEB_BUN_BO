package com.example.demo;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileServiceAvatarTests {
    @Test
    void databaseFailureDeletesNewAvatarButKeepsOldAvatar() {
        UserRepository repository = mock(UserRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        ProfileService service = new ProfileService(repository, storage);
        User user = new User(); user.setAvatarUrl("/uploads/avatars/old.jpg");
        MockMultipartFile file = new MockMultipartFile("avatarFile", "new.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(storage.storeImage(file, "avatars")).thenReturn("/uploads/avatars/new.jpg");
        when(repository.saveAndFlush(any(User.class))).thenThrow(new RuntimeException("db"));

        assertThrows(RuntimeException.class, () -> service.updateProfile(user, "Name", null, null, null, file));
        verify(storage).deleteIfManaged("/uploads/avatars/new.jpg");
        verify(storage, never()).deleteIfManaged("/uploads/avatars/old.jpg");
    }

    @Test
    void successfulUpdateDeletesOldManagedAvatarAndKeepsAvatarWithoutFile() {
        UserRepository repository = mock(UserRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        ProfileService service = new ProfileService(repository, storage);
        User user = new User(); user.setAvatarUrl("/uploads/avatars/old.jpg");
        MockMultipartFile file = new MockMultipartFile("avatarFile", "new.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(storage.storeImage(file, "avatars")).thenReturn("/uploads/avatars/new.jpg");
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateProfile(user, "Name", null, null, null, file);
        verify(storage).deleteIfManaged("/uploads/avatars/old.jpg");
        service.updateProfile(user, "Name 2", null, null, null, null);
        verify(storage, times(1)).storeImage(any(), any());
    }
}
