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
 * Handles sorting/management of authors table @ /authors
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var CURRENT_PAGE = 0;
var ORDERING = "";

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

var newOrdering = function (element) {
    var ordering = element.data("ordering");
    var newOrdering;

    if (ORDERING.startsWith("-")) {
        ORDERING = ORDERING.substr(1, ORDERING.length);

        if (ORDERING === ordering) {
            newOrdering = ORDERING;
        }
    } else if (ORDERING === ordering) {
        newOrdering = "-" + ORDERING;
    } else {
        newOrdering = ordering;
    }

    return newOrdering;
};

$(function () {
    $('table.staff-table > thead > tr > th[data-ordering]').click(function () {
        go(jsRoutes.controllers.Users.showStaff(newOrdering($(this)), CURRENT_PAGE > 1 ? CURRENT_PAGE : null).absoluteURL);
    });

    $('table.authors-table > thead > tr > th[data-ordering]').click(function () {
        go(jsRoutes.controllers.Users.showAuthors(newOrdering($(this)), CURRENT_PAGE > 1 ? CURRENT_PAGE : null).absoluteURL);
    });
});
