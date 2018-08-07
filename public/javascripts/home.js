/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By the Ore team and contributors
 * (C) SpongePowered 2016-2018 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Home page specific script
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var CATEGORY_STRING = null;
var SORT_STRING = null;
var QUERY_STRING = null;

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    // Initialize sorting selection
    $('.select-sort').on('change', function() {
        var sort = $(this).find('option:selected').val();
        go(jsRoutes.controllers.Application.showHome(CATEGORY_STRING, QUERY_STRING, sort, 1, null).absoluteURL);
    });

    var searchBar = $('.project-search');
    searchBar.find('input').on('keypress', function(event) {
        if (event.keyCode === KEY_ENTER) {
            event.preventDefault();
            $(this).next().find('.btn').click();
        }
    });
    searchBar.find('.btn').click(function() {
        var query = $(this).closest('.input-group').find('input').val();
        console.log(CATEGORY_STRING)
        go(jsRoutes.controllers.Application.showHome(CATEGORY_STRING, query, SORT_STRING, 1, null).absoluteURL);
    });
});
