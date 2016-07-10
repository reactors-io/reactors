
var log = {
  error: function(msg, err) {
    console.log(msg);
    throw new Error(err);
  }
};
