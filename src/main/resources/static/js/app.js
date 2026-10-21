(function () {
    'use strict';

    var SUBMIT_SELECTOR = 'button[type="submit"], input[type="submit"]';
    var DEFAULT_FILE_LABEL = '尚未选择文件';

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function todayValue() {
        var now = new Date();
        return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
    }

    function formatSize(bytes) {
        if (bytes >= 1024 * 1024) {
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        }
        return Math.ceil(bytes / 1024) + ' KB';
    }

    function extensionOf(name) {
        var index = name.lastIndexOf('.');
        return index < 0 ? '' : name.slice(index + 1).toLowerCase();
    }

    function isAllowed(file, accept) {
        var extensions = ['jpg', 'jpeg', 'png'];
        if (accept && accept.indexOf('image/') >= 0) {
            if (extensions.indexOf(extensionOf(file.name)) < 0) {
                return false;
            }
            return file.type === '' || accept.indexOf(file.type) >= 0;
        }
        return true;
    }

    /** F2 防重复提交：提交后立即禁用按钮，返回页面时恢复。 */
    function guardForms() {
        var forms = document.querySelectorAll('form[data-guard]');
        Array.prototype.forEach.call(forms, function (form) {
            form.addEventListener('submit', function (event) {
                if (event.defaultPrevented) {
                    return;
                }
                Array.prototype.forEach.call(form.querySelectorAll(SUBMIT_SELECTOR), function (button) {
                    button.disabled = true;
                });
            });
        });

        window.addEventListener('pageshow', function (event) {
            if (!event.persisted) {
                return;
            }
            Array.prototype.forEach.call(forms, function (form) {
                Array.prototype.forEach.call(form.querySelectorAll(SUBMIT_SELECTOR), function (button) {
                    button.disabled = false;
                });
            });
        });
    }

    /** 截止日期不得早于今天：同时设置 min 与后端校验保持一致。 */
    function applyDateMinimums() {
        var today = todayValue();
        Array.prototype.forEach.call(document.querySelectorAll('[data-min-today]'), function (input) {
            input.min = today;
        });
    }

    /** F4 整改照片选择：回显文件名并做与后端一致的前端校验。 */
    function bindFilePickers() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-file-drop]'), function (drop) {
            var input = drop.querySelector('input[type="file"]');
            var nameLabel = drop.querySelector('[data-file-name]');
            if (!input || !nameLabel) {
                return;
            }
            var defaultLabel = nameLabel.textContent;
            var maxSize = Number(input.getAttribute('data-max-size')) || 5 * 1024 * 1024;

            input.addEventListener('change', function () {
                var invalid = false;
                if (input.files.length === 0) {
                    nameLabel.textContent = defaultLabel || DEFAULT_FILE_LABEL;
                } else {
                    var file = input.files[0];
                    nameLabel.textContent = file.name + '（' + formatSize(file.size) + '）';
                    invalid = file.size === 0
                        || file.size > maxSize
                        || !isAllowed(file, input.getAttribute('accept'));
                }
                drop.classList.toggle('is-invalid', invalid);
                var field = drop.closest('.form-field');
                if (field) {
                    field.classList.toggle('is-invalid', invalid);
                }
            });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        guardForms();
        applyDateMinimums();
        bindFilePickers();
    });
})();
