
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

        // Reset alert and buttons
        $('.upload-step1-file').removeClass('alert-success alert-info').addClass('alert-info');
        $('.upload-step1-sig-icon').html('');
        $('.upload-step1-sig-message').text('');
        $('#uploadFileBtn').removeClass('btn-default btn-success btn-primary').addClass('btn-default');
        $('#uploadSignatureBtn').removeClass('btn-default btn-success btn-primary').addClass('btn-default');
        $('#uploadFinishBtn').removeClass('btn-default btn-success btn-primary').addClass('btn-default');
        $('#uploadOwnerSelect').removeClass('btn-default btn-success btn-primary').addClass('btn-default');

        // Show filename and size to user
        $('.upload-step1-file-message').text(fileName + '  –  ' + filesize(this.files[0].size));
        $('.upload-step1-file').show();

        // Show next button
        $('#uploadFileBtn').toggleClass('btn-primary').toggleClass('btn-success');
        $('#uploadSignatureBtn').toggleClass('btn-secondary').toggleClass('btn-primary');
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

        // Next button
        $('#uploadSignatureBtn').toggleClass('btn-primary').toggleClass('btn-success');
        if ($('#uploadOwnerSelect').length) {
            if ($('#inlineFormCustomSelect').val() != null) {
                $('#uploadOwnerSelect').toggleClass('btn-primary').toggleClass('btn-success');
                $('#uploadFinishBtn').toggleClass('btn-default').toggleClass('btn-primary');
            } else {
                $('#uploadOwnerSelect').toggleClass('btn-default').toggleClass('btn-primary');
            }
        } else {
            $('#uploadFinishBtn').toggleClass('btn-default').toggleClass('btn-primary');
        }
        // Show upload messages
        fileName = fileName.substr(fileName.lastIndexOf('\\') + 1, fileName.length);
        $('.upload-step1-sig-icon').html('<i class="fas fa-fw fa-file-contract"></i>');
        $('.upload-step1-sig-message').text(fileName);
    });

    $("#inlineFormCustomSelect").on('change', function() {
        $('#uploadOwnerSelect').removeClass('btn-default btn-success btn-primary').addClass('btn-success');
        $('#uploadFinishBtn').removeClass('btn-default btn-success btn-primary').addClass('btn-primary');
    });

    $("#uploadFinishBtn").on('click', function () {
        // TODO: Fix icon spinning icon
        $('#uploadFinishBtn i').toggleClass('fa-file-upload').toggleClass('fa-spinner fa-spin fas fa-fw');
        $("#uploadFileForm").submit();
    });

});
