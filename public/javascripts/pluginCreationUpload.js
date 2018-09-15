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
    $('#uploadFileInput').on('change', function() {
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

        // Show filename and size to user
        $('.upload-step1-file-message').text(fileName + '  –  ' + filesize(this.files[0].size));
        $('.upload-step1-file').show();

        // Show next button
        $('#uploadFileBtn').toggleClass('btn-primary').toggleClass('btn-success');
        $('#uploadSignaterBtn').toggleClass('btn-secondary').toggleClass('btn-primary');
    });

    $('#uploadSignatureInput').on('change', function() {
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

        $('.upload-step1-file').toggleClass('alert-info').toggleClass('alert-success');

        $('#uploadSignaterBtn').toggleClass('btn-primary').toggleClass('btn-success');
        $('#uploadFinishBtn').toggleClass('btn-secondary').toggleClass('btn-primary');

        // Show upload messages
        fileName = fileName.substr(fileName.lastIndexOf('\\') + 1, fileName.length);
        $('.upload-step1-sig-icon').html('<i class="fas fa-fw fa-file-contract"></i>');
        $('.upload-step1-sig-message').text(fileName);
    });

    $("#uploadFinishBtn").on('click', function () {
        // TODO: Fix icon spinning icon
        $('#uploadFinishBtn i').toggleClass('fa-file-upload').toggleClass('fa-spinner fa-spin');
        $("#uploadFileForm").submit();
    });

});
