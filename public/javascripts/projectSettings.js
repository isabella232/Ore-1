/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

var generateConfirmButton = function(buttonSelector, formSelector) {
    var firstClick = false;

    var button = $(buttonSelector);
    button.click(function () {
        if(firstClick) {
            $(formSelector).submit();
        } else {
            firstClick = true;
            button.text("Confirm");
        }
    });
};

$(function() {
    generateConfirmButton(".rename-setting #rename-button", ".rename-setting #rename-form");
    generateConfirmButton(".hardDelete-setting #hardDelete-button", ".hardDelete-setting #hardDelete-form");
    generateConfirmButton(".delete-setting #delete-button", ".delete-setting #delete-form");

    $(".visibility-setting select").change(function () {
        if(this.value === "3" || this.value === "5") {
            $("#reason-form").show();
        } else {
            $("#reason-form").hide();
        }
    });

    $('.dropdown-license').find('a').click(function() {
        var btn = $('.btn-license');
        var text = $(this).text();
        btn.text(text);
        var name = $('input[name="license-name"]');
        if ($(this).hasClass('license-custom')) {
            name.val('');
            name.show().focus();
        } else {
            name.hide();
            name.val(text);
        }
    });


});
