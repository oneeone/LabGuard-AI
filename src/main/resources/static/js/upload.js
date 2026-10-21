(function () {
    'use strict';

    var MAX_FILE_SIZE = 5 * 1024 * 1024;
    var ALLOWED_TYPES = ['image/jpeg', 'image/png'];
    var ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png'];
    var DEFAULT_FILE_LABEL = '尚未选择文件';
    var SUBMIT_LABEL = '提交并开始 AI 分析';
    var LOADING_LABEL = 'AI 分析中…';

    function pad(value) {
        return String(value).padStart(2, '0');
    }

    function toLocalDateTimeValue(date) {
        return date.getFullYear()
            + '-' + pad(date.getMonth() + 1)
            + '-' + pad(date.getDate())
            + 'T' + pad(date.getHours())
            + ':' + pad(date.getMinutes());
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

    function isAllowedFile(file) {
        if (ALLOWED_EXTENSIONS.indexOf(extensionOf(file.name)) < 0) {
            return false;
        }
        return ALLOWED_TYPES.indexOf(file.type) >= 0 || file.type === '';
    }

    function markInvalid(element, invalid) {
        var field = element.closest('.form-field');
        if (!field) {
            return;
        }
        field.classList.toggle('is-invalid', invalid);
        var drop = field.querySelector('.file-drop');
        if (drop) {
            drop.classList.toggle('is-invalid', invalid);
        }
    }

    function clearInvalid(inputs) {
        inputs.forEach(function (input) {
            if (input) {
                markInvalid(input, false);
            }
        });
    }

    function isValidFile(fileInput) {
        if (fileInput.files.length === 0) {
            return false;
        }
        var file = fileInput.files[0];
        return isAllowedFile(file) && file.size > 0 && file.size <= MAX_FILE_SIZE;
    }

    function isValidText(input, minLength, maxLength) {
        var trimmed = input.value.trim();
        return trimmed.length >= minLength && input.value.length <= maxLength;
    }

    function isValidInspectionTime(input) {
        if (input.value === '') {
            return true;
        }
        var selected = new Date(input.value);
        if (Number.isNaN(selected.getTime())) {
            return false;
        }
        return selected.getTime() <= Date.now();
    }

    document.addEventListener('DOMContentLoaded', function () {
        var form = document.getElementById('inspectionForm');
        var fileInput = document.getElementById('file');
        var fileName = document.getElementById('fileName');
        var labNameInput = document.getElementById('labName');
        var inspectorInput = document.getElementById('inspector');
        var inspectionTimeInput = document.getElementById('inspectionTime');
        var noteInput = document.getElementById('note');
        var submitBtn = document.getElementById('submitBtn');

        if (!form || !fileInput || !fileName || !submitBtn) {
            return;
        }

        if (inspectionTimeInput) {
            inspectionTimeInput.max = toLocalDateTimeValue(new Date());
        }

        fileInput.addEventListener('change', function () {
            if (fileInput.files.length === 0) {
                fileName.textContent = DEFAULT_FILE_LABEL;
                markInvalid(fileInput, false);
                return;
            }
            var file = fileInput.files[0];
            fileName.textContent = file.name + '（' + formatSize(file.size) + '）';
            markInvalid(fileInput, !isValidFile(fileInput));
        });

        [labNameInput, inspectorInput, inspectionTimeInput, noteInput].forEach(function (input) {
            if (!input) {
                return;
            }
            input.addEventListener('input', function () {
                markInvalid(input, false);
            });
        });

        form.addEventListener('reset', function () {
            window.setTimeout(function () {
                fileName.textContent = DEFAULT_FILE_LABEL;
                clearInvalid([fileInput, labNameInput, inspectorInput, inspectionTimeInput, noteInput]);
            }, 0);
        });

        form.addEventListener('submit', function (event) {
            var invalidInputs = [];

            if (!isValidFile(fileInput)) {
                invalidInputs.push(fileInput);
            }
            if (labNameInput && !isValidText(labNameInput, 1, 50)) {
                invalidInputs.push(labNameInput);
            }
            if (inspectorInput && !isValidText(inspectorInput, 1, 30)) {
                invalidInputs.push(inspectorInput);
            }
            if (inspectionTimeInput && !isValidInspectionTime(inspectionTimeInput)) {
                invalidInputs.push(inspectionTimeInput);
            }
            if (noteInput && noteInput.value.length > 200) {
                invalidInputs.push(noteInput);
            }

            if (invalidInputs.length > 0) {
                event.preventDefault();
                invalidInputs.forEach(function (input) {
                    markInvalid(input, true);
                });
                invalidInputs[0].focus();
                return;
            }

            submitBtn.disabled = true;
            submitBtn.textContent = LOADING_LABEL;
        });

        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                submitBtn.disabled = false;
                submitBtn.textContent = SUBMIT_LABEL;
            }
        });
    });
})();