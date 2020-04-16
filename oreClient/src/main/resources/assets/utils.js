import config from "./config.json5"

export function clearFromEmpty(object) {
    return Object.entries(object)
        .filter(([key, value]) => value != null && value !== "")
        .reduce((acc, [key, value]) => ({...acc, [key]: value}), {})
}

export function clearFromDefaults(object, defaults) {
    return Object.entries(object)
        .filter(([key, value]) => {
            if(value instanceof Array && defaults[key] instanceof Array) {
                return value.length !== defaults[key].length;
            }

            return value !== defaults[key];
        })
        .reduce((acc, [key, value]) => ({...acc, [key]: value}), {})
}

export function parseJsonOrNull(jsonString) {
    try {
        return JSON.parse(jsonString);
    } catch (e) {
        return null;
    }
}

//https://stackoverflow.com/a/4673436/7207457
export function formatString(format) {
    let args = Array.prototype.slice.call(arguments, 1);
    return format.replace(/{(\d+)}/g, function(match, number) {
        return typeof args[number] != 'undefined'
            ? args[number]
            : match
            ;
    });
};

export function avatarUrl(name) {
    if (name === 'Spongie') {
        return config.sponge.logo;
    }
    else {
        return formatString(config.security.api.avatarUrl, name)
    }
}
