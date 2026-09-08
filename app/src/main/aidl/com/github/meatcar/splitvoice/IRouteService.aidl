package com.github.meatcar.splitvoice;
import android.os.Bundle;
interface IRouteService {
    void destroy() = 16777114;
    Bundle inspect(String receiverAddress, String earbudAddress) = 1;
    Bundle apply(String receiverAddress, String earbudAddress) = 2;
    Bundle restore(String receiverAddress, String earbudAddress) = 3;
}
