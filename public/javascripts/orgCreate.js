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
 * Validates and handles organization creation.
 *
 * ==================================================
 */

var MIN_NAME_LENGTH = 3;

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function resetStatus(status) {
    return status.removeClass('fa-spinner fa-spin')
        .removeClass('fa-check-circle')
        .removeClass('fa-times-circle')
        .removeClass('fa-search');
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    var events = 0;

    $('.input-name').on('input', function() {
        var val = $(this).val();
        var statusIcon = $('.status-org-name-container').find('[data-fa-i2svg]');
        var continueBtn = $('.continue-btn').prop('disabled', true);
        if (val.length < MIN_NAME_LENGTH)
            resetStatus(statusIcon).addClass('fas fa-fw fa-search');
        else {
            resetStatus(statusIcon).addClass('fas fa-fw fa-spinner fa-spin');
            statusIcon.show();
            var event = ++events;
            $.ajax({
                url: '/' + val,
                statusCode: {
                    404: function() {
                        if (event === events) {
                            resetStatus(statusIcon).addClass('fas fa-fw fa-check-circle');
                            continueBtn.prop('disabled', false);
                        }
                    },
                    200: function() {
                        if (event === events)
                            resetStatus(statusIcon).addClass('fas fa-fw fa-times-circle');
                    }
                }
            });
        }
    });
});
