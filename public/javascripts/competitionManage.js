$(function() {

    String.prototype.format = function () {
        var args = [].slice.call(arguments);
        return this.replace(/(\{\d+\})/g, function (a){
            return args[+(a.substr(1,a.length-2))||0];
        });
    };

    function int2digits(i) {
        return i > 9 ? "" + i : "0" + i;
    }

    setInterval(function() {

        $('.counter').each(function() {
            var time = $(this).text();
            var sep1 = time.indexOf(' ');
            var sep2 = time.indexOf(':');
            var sep3 = time.lastIndexOf(':');
            var seconds = parseInt(time.substring(sep3 + 1).trim());
            var minutes = parseInt(time.substring(sep2 + 1, sep3));
            var hours = parseInt(time.substring(time.indexOf(' ') + 1, sep2));
            var days = parseInt(time.substring(0, sep1));
            var totalSeconds = days * 86400 + hours * 3600 + minutes * 60 + seconds - 1;
            var newTime = "{0} {1}:{2}:{3}".format(
                Math.floor(totalSeconds / 86400),
                Math.floor((totalSeconds % 86400) / 3600),
                int2digits(Math.floor((totalSeconds % 3600) / 60)),
                int2digits(Math.floor((totalSeconds % 60))));

            console.log(newTime);

            $(this).text(newTime);
        });

    }, 1000);

});
