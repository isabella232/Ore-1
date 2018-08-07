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
 * Uploads project icons via AJAX
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var PROJECT_OWNER = null;
var PROJECT_SLUG = null;

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    var form = $('#form-icon');
    var btn = form.find('.btn-upload');
    var preview = form.find('.preview');
    var input = form.find('input[type="file"]');

    function updateButton() {
        btn.popover('hide');
        btn.prop('disabled', input[0].files.length === 0);
    }

    input.on('change', function(e) {
        updateButton();

        var fileName = e.target.files[0].name;
        $(this).next('.custom-file-label').text(fileName);
    });

    var formData = new FormData(form[0]);
    formData.append('csrfToken', csrf);

    // Upload button
    btn.click(function(e) {
        e.preventDefault();
        btn.find('svg').removeClass('fa-upload').addClass('fa-spinner fa-spin');

        var r = jsRoutes.controllers.project.Projects.uploadIcon(PROJECT_OWNER, PROJECT_SLUG);

        $.ajax({
            url: r.url,
            type: r.type,
            data: new FormData(form[0]),
            cache: false,
            contentType: false,
            processData: false,
            complete: function() {
                preview.attr('src', jsRoutes.controllers.project.Projects.showPendingIcon(PROJECT_OWNER, PROJECT_SLUG).absoluteURL() + "?" + new Date().getTime());
                btn.find('svg').removeClass('fa-spinner fa-spin').addClass('fa-upload');
                btn.find('span').text('Uploaded');
                btn.removeClass('btn-info').addClass('btn-success');
            },
            success: function() {
                $('#update-icon').val('true');
                input.val('');
                updateButton();
                btn.popover({
                    content: "Don't forget to save changes!",
                    trigger: "manual",
                    boundary: "window"
                });
                btn.popover('show');
            }
        });
    });

    // Reset button
    var reset = form.find('.btn-reset');
    reset.click(function(e) {
        e.preventDefault();
        reset.find('svg').removeClass('fa-undo').addClass('fa-spinner fa-spin');

        $.ajax(jsRoutes.controllers.project.Projects.resetIcon(PROJECT_OWNER, PROJECT_SLUG)).done(function () {
            preview.attr('src', jsRoutes.controllers.project.Projects.showIcon(PROJECT_OWNER, PROJECT_SLUG).absoluteURL() + "?" + new Date().getTime());
            input.val('');
            updateButton();
            reset.find('svg').removeClass('fa-spinner fa-spin').addClass('fa-undo');
            reset.popover({
                content: "The project icon has been reset!",
                trigger: "manual",
                boundary: "window",
                placement: "top"
            });
            reset.popover('show');
            setTimeout(function () {
                reset.popover('hide');
            }, 3000);
        }).fail(function () {
            reset.find('svg').removeClass('fa-spinner fa-spin').addClass('fa-undo');
            reset.popover({
                content: "An error occured while resetting the icon! Try again.",
                trigger: "manual",
                boundary: "window",
                placement: "top"
            });
            reset.popover('show');
            setTimeout(function () {
                reset.popover('hide');
            }, 3000);
        });
    });
});
