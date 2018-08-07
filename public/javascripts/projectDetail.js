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
 * Powers the main project landing. Also implements the content editor seen on
 * project pages, version pages, and discussion tab.
 *
 * ==================================================
 */

var KEY_J = 74;
var KEY_K = 75;

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var projectOwner = null;
var projectSlug = null;
var alreadyStarred = false;

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function getActiveTab() {
    return $('.project-navbar').find('li.active');
}

function switchTabTo(tab, def) {
    var id = tab.attr('id');
    if (tab.is('li') && id !== 'issues' && id !== 'source') {
        window.location = tab.find('a').attr('href');
    } else {
        window.location = def.find('a').attr('href');
    }
}

function initFlagList() {
    var flagList = $('.list-flags');
    if (!flagList.length) return;
    flagList.find('li').click(function () {
        flagList.find(':checked').removeAttr('checked');
        $(this).find('input').prop('checked', true);
    });
}

var editing = false;
var previewing = false;

function initBtnEdit() {
    var btnEdit = $('.btn-edit');
    if (!btnEdit.length) return;

    var pageBtns = $('.btn-page');
    var otherBtns = $('.btn-edit-container');

    // if page contains
    otherBtns.hide();

    // edit button click
    $('.btn-edit').click(function () {
        var editor = $('.page-edit');

        editing = true;
        previewing = false;

        // open editor
        var content = $('.page-rendered');
        editor.find('textarea').css('height', content.css('height'));
        content.hide();
        editor.show();

        // show buttons
        $(otherBtns).show();
        $('.btn-edit').removeClass('text-dark');
        $('.btn-preview').addClass('text-dark');
    });

    // preview button click
    $('.btn-preview').click(function () {
        var editor = $('.page-edit');
        var preview = $('.page-preview');
        var raw = editor.find('textarea').val();

        editor.hide();
        preview.show();

        var icon = $(this).find('i svg');
        icon.removeClass('fa-eye').addClass('fa-circle-notch fa-spin');

        $.ajax({
            type: 'post',
            url: '/pages/preview?csrfToken=' + csrf,
            data: JSON.stringify({raw: raw}),
            contentType: 'application/json',
            dataType: 'html',
            complete: function () {
                icon.removeClass('fa-circle-notch fa-spin').addClass('fa-eye');
            },
            success: function (cooked) {
                preview.html(cooked);
            }
        });

        editing = false;
        previewing = true;
        $('.btn-edit').addClass('text-dark').css('color', null);
        $('.btn-preview').removeClass('text-dark');
    });

    // Save button click
    $('.btn-save').click(function () {
        $(this).find('i').removeClass('icon-save');
        $('#form-editor-save').submit();
    });

    // Cancel button click
    $('.btn-cancel').click(function () {
        // Reset defaults
        editing = false;
        previewing = false;
        $('.btnEdit').removeClass('open');

        // hide editor; show content
        $('.page-edit').hide();
        $('.page-preview').hide();
        $('.page-content').show();

        // hide buttons
        otherBtns.hide();
        $('.btn-edit').addClass('text-dark');
        $('.btn-preview').addClass('text-dark');
    });

    // move with scroll
    $(window).scroll(function () {
        var scrollTop = $(this).scrollTop();
        var editHeight = btnEdit.height();
        var page = previewing ? $('.page-preview') : $('.page-content');
        var pageTop = page.position().top;
        var pto = page.offset().top;
        var pos = btnEdit.css('position');
        var bound = pto - editHeight - 30;

        if (scrollTop > bound && pos === 'absolute' && !editing) {
            var newTop = pageTop + editHeight + 20;
            btnEdit.css('position', 'fixed').css('top', newTop);
            otherBtns.each(function () {
                newTop += 0.5;
                $(this).css('position', 'fixed').css('top', newTop);
            });
        } else if (scrollTop < bound && pos === 'fixed') {
            btnEdit.css('position', 'absolute').css('top', '');
            otherBtns.css('position', 'absolute').css('top', '');
        }
    });
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function () {
    initFlagList();
    initBtnEdit();

    // flag button alert
    var flagMsg = $('.flag-msg');
    if (flagMsg.length) {
        flagMsg.hide().fadeIn(1000).delay(2000).fadeOut(1000);
    }

    // watch button
    $('.btn-watch').click(function () {
        var status = $(this).find('.watch-status');
        var watching = $(this).hasClass('watching');
        if (watching) {
            status.text('Watch');
            $(this).removeClass('watching');
        } else {
            status.text('Unwatch');
            $(this).addClass('watching');
        }

        $.ajax({
            type: 'post',
            url: decodeHtml('/' + projectOwner + '/' + projectSlug) + '/watch/' + !watching,
            data: {csrfToken: csrf}
        });
    });

    // setup star button
    var increment = alreadyStarred ? -1 : 1;
    $('.btn-star').click(function () {
        var starred = $(this).find('.starred');
        starred.html(' ' + (parseInt(starred.text()) + increment).toString());
        $.ajax({
            type: 'post',
            url: decodeHtml('/' + projectOwner + '/' + projectSlug) + '/star/' + (increment > 0),
            data: {csrfToken: csrf}
        });

        var icon = $('#icon-star');
        if (increment > 0) {
            icon.removeClass('fa-star-o').addClass('fa-star');
        } else {
            icon.removeClass('fa-star').addClass('fa-star-o');
        }

        increment *= -1;
    });

    // hotkeys
    var body = $('body');
    body.keydown(function (event) {
        var target = $(event.target);
        if (target.is('body') && shouldExecuteHotkey(event)) {
            var navBar = $('.project-navbar');
            switch (event.keyCode) {
                case KEY_J:
                    event.preventDefault();
                    switchTabTo(getActiveTab().next(), navBar.find('li:first'));
                    break;
                case KEY_K:
                    event.preventDefault();
                    switchTabTo(getActiveTab().prev(), navBar.find('#discussion'));
                    break;
                default:
                    break;
            }
        }
    });
});
