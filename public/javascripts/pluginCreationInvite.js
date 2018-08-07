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
 * Manage the invitation process.
 *
 * ==================================================
 */

var KEY_ENTER = 13;


$(function() {
    $('a[data-author]').on('click', function() {
        addUserAttempt($(this).attr('data-author'));
        $(this).parent().hide();
    });
    $('.user-search-input').on('keypress', function(event) {
        if (event.keyCode === KEY_ENTER) {
            event.preventDefault();
            $('.user-search-btn').click();
        }
    });
    $('.user-search-btn').on('click', function() {
        addUserAttempt($('.user-search-input').val());
        $('.user-search-input').val('');
    });
});

function addUserAttempt(username) {
    // Check if user exists
    if (username.length === 0) {
        return;
    }
    $.ajax({
        url: '/api/users/' + username,
        dataType: 'json',
        complete: function() {},
        error: function() {
            alert('User not available');
        },
        success: function(user) {
            addUserRow(user);
        }
    });
}

function addUserRow(user) {
    // Sanity check if user is already in list (server-side will take highest role if found twice)
    var isAlreadyInList = false;
    $('input[name^=users]').each(function() {
        if ($(this).val() == user.id) {
            isAlreadyInList = true;
            return;
        }
    });

    if (isAlreadyInList) {
        alert('User is already in list');
    } else {
        // Copy template
        var newEntry = $('.table-project-creation-invite tbody tr:first-child').clone();

        // Edit entry
        $(newEntry).find('td:nth-child(1) a').attr('href', user.username);
        $(newEntry).find('td:nth-child(1) img').attr('src', user.avatarUrl);
        $(newEntry).find('td:nth-child(2)').append(user.username);
        $(newEntry).find('td:nth-child(2) input').val(user.id).attr('name', 'users[]');
        $(newEntry).find('td:nth-child(3) select').attr('name', 'roles[]');

        // Append entry
        $('.table-project-creation-invite tbody').append(newEntry);
    }
}

