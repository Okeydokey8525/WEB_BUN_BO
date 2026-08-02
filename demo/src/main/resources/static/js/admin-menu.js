(() => {
    const placeholder = '/images/placeholders/menu-item.svg';
    const allowedExtensions = new Set(['jpg', 'jpeg', 'png', 'webp']);
    const maxBytes = 5 * 1024 * 1024;
    let objectUrl;

    const preview = () => document.getElementById('menu-image-preview');
    const error = () => document.getElementById('menu-image-error');

    function showError(message) {
        const element = error();
        if (!element) return;
        element.textContent = message;
        element.hidden = false;
    }

    function clearError() {
        const element = error();
        if (!element) return;
        element.textContent = '';
        element.hidden = true;
    }

    function revokeObjectUrl() {
        if (objectUrl) {
            URL.revokeObjectURL(objectUrl);
            objectUrl = undefined;
        }
    }

    window.setMenuImagePreview = (path) => {
        revokeObjectUrl();
        const image = preview();
        if (image) image.src = path || placeholder;
        clearError();
    };

    document.addEventListener('DOMContentLoaded', () => {
        const input = document.getElementById('imageFile');
        if (!input) return;

        input.addEventListener('change', () => {
            const file = input.files && input.files[0];
            if (!file) return;

            const extension = file.name.includes('.')
                ? file.name.split('.').pop().toLowerCase()
                : '';
            if (!allowedExtensions.has(extension)) {
                input.value = '';
                showError('Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP.');
                return;
            }
            if (file.size > maxBytes) {
                input.value = '';
                showError('Ảnh phải có dung lượng tối đa 5 MB.');
                return;
            }

            clearError();
            revokeObjectUrl();
            objectUrl = URL.createObjectURL(file);
            const image = preview();
            if (image) image.src = objectUrl;
        });

        window.addEventListener('beforeunload', revokeObjectUrl, { once: true });
    });
})();
