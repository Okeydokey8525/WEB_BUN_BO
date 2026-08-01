(() => {
    const placeholder = '/images/placeholders/avatar.svg';
    let objectUrl;
    document.addEventListener('DOMContentLoaded', () => {
        const input = document.getElementById('avatarFile');
        const preview = document.getElementById('avatarPreview');
        const error = document.getElementById('avatarFileError');
        if (!input || !preview) return;
        input.addEventListener('change', () => {
            const file = input.files && input.files[0];
            if (!file) return;
            const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
            const valid = ['jpg', 'jpeg', 'png', 'webp'].includes(extension) && file.size <= 5 * 1024 * 1024;
            if (!valid) {
                input.value = '';
                if (error) { error.textContent = 'Chỉ hỗ trợ JPG, PNG, WebP với dung lượng tối đa 5 MB.'; error.hidden = false; }
                preview.src = preview.dataset.currentAvatar || placeholder;
                return;
            }
            if (objectUrl) URL.revokeObjectURL(objectUrl);
            objectUrl = URL.createObjectURL(file);
            preview.src = objectUrl;
            if (error) { error.textContent = ''; error.hidden = true; }
        });
    });
})();
