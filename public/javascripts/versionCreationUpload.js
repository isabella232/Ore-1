/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By Walker Crouse (windy) and contributors
 * (C) SpongePowered 2016-2017 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Validates and handles new plugin uploads.
 *
 * ==================================================
 */

/*
 * STEP ONE
 */
var MAX_FILE_SIZE = 20971520;

$(function() {
    $(document).on('change', '.upload-file-input', function() {
        // If we don't have any files cancel
        if (this.files.length == 0) {
            return;
        }
        // Get path of file
        var fileName = $(this).val().trim();
        var fileSize = this.files[0].size;

        // Check if file was uploaded, if not cancel
        if (!fileName) {
            $('.upload-step1-error-message').text('An unknown error occerd when uploading. Did you select a file?');
            $('.upload-step1-error').show();
            return;
        }

        // Check size of file against max size
        if (fileSize > MAX_FILE_SIZE) {
            $('.upload-step1-error-message').text('That file is too big. Plugins may be no larger than ' + filesize(MAX_FILE_SIZE) + '.');
            $('.upload-step1-error').show();
            return;
        } else if (!fileName.endsWith('.zip') && !fileName.endsWith('.jar')) {
            $('.upload-step1-error-message').text('Only JAR and ZIP files are accepted.');
            $('.upload-step1-error').show();
            return;
        }

        // Remove fake path in filename
        fileName = fileName.substr(fileName.lastIndexOf('\\') + 1, fileName.length);

        var mainDialog = $(this).parents('.alert');

        // Reset alert and buttons
        $(mainDialog).removeClass('alert-success alert-info').addClass('alert-info');
        $(mainDialog).find('.upload-step1-sig-icon').html('');
        $(mainDialog).find('.upload-step1-sig-message').text('');
        $(mainDialog).find('.upload-file-btn').removeClass('btn-success btn-primary').addClass('btn-secondary');
        $(mainDialog).find('.upload-signature-btn').removeClass('btn-success btn-primary').addClass('btn-secondary');
        $('#uploadFinishBtn').removeClass('btn-success btn-primary').addClass('btn-secondary');

        // Show filename and size to user
        $(mainDialog).find('.upload-step1-file-icon').html('<i class="fas fa-fw fa-file-alt"></i>');
        $(mainDialog).find('.upload-step1-file-message').text(fileName + '  –  ' + filesize(this.files[0].size));
        $(mainDialog).find('.upload-step1-file').show();

        // Show next button
        $(mainDialog).find('.upload-file-btn').toggleClass('btn-primary').toggleClass('btn-success');
        $(mainDialog).find('.upload-signature-btn').toggleClass('btn-secondary').toggleClass('btn-primary');
    });

    $(document).on('change', '.upload-signature-input', function() {
        // Get path of file
        var fileName = $(this).val().trim();

        // Check if file was uploaded, if not cancel
        if (!fileName) {
            return;
        }

        // Check if file is allowed
        if (!fileName.endsWith('.sig') && !fileName.endsWith('.asc')) {
            $('.upload-step1-error-message').text('Only SIG and ASC files are accepted for signatures.');
            $('.upload-step1-error').show();
            return;
        }

        // Update buttons
        $('#uploadFinishBtn').toggleClass('btn-secondary').toggleClass('btn-primary');
        $('#addAnotherVersion').removeClass('btn-secondary').addClass('btn-primary');

        // Get container
        var mainDialog = $(this).parents('.alert');

        $(mainDialog).toggleClass('alert-info').toggleClass('alert-success');
        $(mainDialog).find('.upload-signature-btn').toggleClass('btn-primary').toggleClass('btn-success');

        fileName = fileName.substr(fileName.lastIndexOf('\\') + 1, fileName.length);
        $(mainDialog).find('.upload-step1-sig-icon').html('<i class="fas fa-fw fa-file-contract"></i>');
        $(mainDialog).find('.upload-step1-sig-message').text(fileName);
    });

    $("#uploadFinishBtn").on('click', function () {
        // TODO: Fix icon spinning icon
        $('#uploadFinishBtn i').toggleClass('fa-file-upload').toggleClass('fa-spinner fa-spin fas fa-fw');
        $("#uploadFileForm").submit();
    });

    $('#addAnotherVersion').on('click', function() {
        addUploadDialog();
    });

    addUploadDialog();

});

function addUploadDialog() {
    // Copy
    var newEntry = $('.upload-container .alert:first-child').clone();

    // Edit settings
    $(newEntry).find('.upload-file-input').attr('name', 'pluginFile-' + $('.upload-container .alert').length);
    $(newEntry).find('.upload-signature-input').attr('name', 'pluginSig-' + + $('.upload-container .alert').length);

    // Append entry
    $('.upload-container').append(newEntry);
}
